"""Judge one scene's measurements against the budget rows (S0025 sections 7 and 8).

Inputs are whatever the collectors wrote: a sampler CSV (and the control run's CSV), a frames JSON, a latency JSON,
an events JSON. A row whose input is absent is not evaluated and is listed as such; a run in which no row could be
evaluated judged nothing and exits 2. Exit 0: every evaluated row within budget; 1: a row over budget (a leak
finding needs the growth band clear of the control band); 2: a row that cannot be decided (growth indistinguishable
from the system's churn, a band straddling the budget, too few samples), or nothing evaluated.
"""
import json
import statistics

from . import sampler as sampler_module
from .stats import (GROWTH_OVER, GROWTH_WITHIN, fit_slope, judge_growth, percentile)
from .verdict import CouldNotVerify, COULD_NOT_VERIFY, FAIL, PASS

OK, OVER, UNDECIDED, SKIPPED = 'ok', 'over', 'undecided', 'skipped'


def load_budgets(path):
    with open(path, encoding='utf-8') as handle:
        data = json.load(handle)
    if not data.get('rows'):
        raise CouldNotVerify(f'{path} has no budget rows')
    return data['rows']


def _series(columns, name):
    if name == 'store_kib':
        cache, files = columns.get('cache_kib'), columns.get('files_kib')
        if cache is None or files is None:
            return None
        values = [None if a is None or b is None else a + b for a, b in zip(cache, files)]
    else:
        values = columns.get(name)
    if values is None or all(v is None for v in values):
        return None
    return values


def _resolve(source, inputs):
    kind, _, key = source.partition(':')
    if kind == 'sampler':
        columns = inputs.get('sampler')
        return None if columns is None else _series(columns, key)
    document = inputs.get(kind)
    if document is None:
        return None
    return document.get(key)


def _floor(source, inputs):
    """The instrument floor (ms) of the document a value came from, if the collector measured one. A value timed
    through adb is the true time plus at most one input call and one UI read."""
    kind = source.partition(':')[0]
    document = inputs.get(kind) if kind != 'sampler' else None
    return document.get('floor_ms') if isinstance(document, dict) else None


def _bracket(measured, limit, floor, text):
    """With a floor the reading is a bracket [measured - floor, measured]: within budget when its upper end is, over
    budget only when its lower end is, and otherwise the instrument cannot resolve the budget (undecided)."""
    if floor is None:
        return (OK if measured <= limit else OVER), text
    if measured <= limit:
        return OK, f'{text} (upper bound; instrument floor {floor:g} ms)'
    if measured - floor > limit:
        return OVER, f'{text} (lower bound {measured - floor:g} ms; instrument floor {floor:g} ms)'
    return UNDECIDED, f'{text}: the true value lies in [{max(measured - floor, 0):g}, {measured:g}] ms, the instrument floor is {floor:g} ms and cannot resolve the budget'


def _limit(row, mode):
    if 'limit_by_mode' in row:
        return row['limit_by_mode'].get(mode)
    return row.get('limit')


def evaluate_row(row, inputs, mode, warmup_s):
    """-> (state, detail)."""
    if row.get('modes') and mode not in row['modes']:
        return SKIPPED, f'row applies to mode {"/".join(row["modes"])}, this is {mode}'
    value = _resolve(row['source'], inputs)
    if value is None:
        return SKIPPED, f'no input for {row["source"]}'
    kind, limit = row['kind'], _limit(row, mode)
    if limit is None and kind not in ('level_band',):
        return SKIPPED, f'no limit for mode {mode}'
    floor = _floor(row['source'], inputs)
    if kind == 'event_max' or kind == 'max':
        value = max(value) if isinstance(value, list) else value
        return _bracket(value, limit, floor, f'{value:g} vs <= {limit:g} {row["unit"]}')
    if kind == 'percentile_max':
        got = percentile(value, row['percentile'])
        return _bracket(got, limit, floor, f'p{row["percentile"]} {got:g} vs <= {limit:g} {row["unit"]} over {len(value)} value(s)')
    columns = inputs['sampler']
    seconds = columns['elapsed_s']
    after = [(t, v) for t, v in zip(seconds, value) if v is not None and t >= warmup_s]
    if kind == 'level_band':
        if len(after) < 8:
            return UNDECIDED, f'{len(after)} sample(s) after warm-up'
        levels = [v for _, v in after]
        low, high = percentile(levels, 5), percentile(levels, 95)
        inside = row['min'] <= low and high <= row['max']
        return (OK if inside else OVER), f'p5..p95 {low:g}..{high:g} vs {row["min"]:g}..{row["max"]:g} {row["unit"]}'
    if kind == 'deviation_max':
        if len(after) < 8:
            return UNDECIDED, f'{len(after)} sample(s) after warm-up'
        levels = [v for _, v in after]
        middle = statistics.median(levels)
        deviation = max(abs(v - middle) for v in levels)
        return (OK if deviation <= limit else OVER), f'max deviation {deviation:g} from median {middle:g} vs <= {limit:g} {row["unit"]}'
    if kind == 'slope_per_hour_max':
        duration = (max(t for t, _ in after) - min(t for t, _ in after)) if after else 0
        if duration < row.get('min_duration_s', 0):
            return UNDECIDED, f'window {duration:.0f} s is shorter than the {row["min_duration_s"]} s the budget needs'
        try:
            measured = fit_slope(seconds, value, warmup_s)
        except CouldNotVerify as error:
            return UNDECIDED, str(error)
        control = None
        control_columns = inputs.get('control')
        if control_columns is not None:
            control_values = _series(control_columns, row['source'].partition(':')[2])
            if control_values is not None:
                try:
                    control = fit_slope(control_columns['elapsed_s'], control_values, warmup_s)
                except CouldNotVerify:
                    control = None
        state, reason = judge_growth(measured, control, limit)
        return {GROWTH_WITHIN: OK, GROWTH_OVER: OVER}.get(state, UNDECIDED), f'{reason} ({row["unit"]}, {measured.n} samples)'
    raise CouldNotVerify(f'budget row {row["id"]} has an unknown kind {kind!r}')


def evaluate(rows, inputs, mode, warmup_s):
    sampled = inputs.get('sampler')
    if sampled is not None:
        lost = len([ok for ok in sampled['ok'] if not ok])
        if lost / len(sampled['ok']) > sampler_module.LOST_LIMIT:
            raise CouldNotVerify(f'the sampler lost {lost} of {len(sampled["ok"])} samples (more than {sampler_module.LOST_LIMIT:.0%}): invalid run')
    results = []
    for row in rows:
        state, detail = evaluate_row(row, inputs, mode, warmup_s)
        results.append((row, state, detail))
    return results


def load_inputs(sampler_csv=None, control_csv=None, frames=None, latency=None, events=None):
    inputs = {}
    if sampler_csv:
        inputs['sampler'] = sampler_module.read_csv(sampler_csv)
    if control_csv:
        inputs['control'] = sampler_module.read_csv(control_csv)
    for name, path in (('frames', frames), ('latency', latency), ('events', events)):
        if path:
            with open(path, encoding='utf-8') as handle:
                inputs[name] = json.load(handle)
    return inputs


def judge(results):
    """-> (code, detail). Prints one line per row first (the caller owns the verdict line)."""
    for row, state, detail in results:
        tag = 'hypothesis' if row.get('status', 'hypothesis') == 'hypothesis' else 'promoted'
        print(f'{row["id"]:5} {state.upper():9} {detail}  [{tag}] {row["title"]}')
    evaluated = [r for r in results if r[1] != SKIPPED]
    if not evaluated:
        return COULD_NOT_VERIFY, 'no budget row had an input: nothing was judged'
    over = [r for r in evaluated if r[1] == OVER]
    undecided = [r for r in evaluated if r[1] == UNDECIDED]
    hypothesis = len([r for r in evaluated if r[0].get('status', 'hypothesis') == 'hypothesis'])
    if over:
        return FAIL, f'{len(over)} of {len(evaluated)} rows over budget: ' + ', '.join(r[0]['id'] for r in over)
    if undecided:
        return COULD_NOT_VERIFY, f'{len(undecided)} of {len(evaluated)} rows undecided: ' + ', '.join(r[0]['id'] for r in undecided)
    return PASS, f'{len(evaluated)} budget row(s) within budget, {hypothesis} still hypothesis'
