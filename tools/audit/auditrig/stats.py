"""Growth slopes with a confidence band, and the verdict logic around a control run (S0025 section 7, items 3-5)."""
import math
from dataclasses import dataclass

from .verdict import CouldNotVerify

_Z = {0.90: 1.6449, 0.95: 1.9600, 0.99: 2.5758}


def t_quantile(df, level=0.95):
    """Two-sided Student-t critical value (Cornish-Fisher expansion about the normal quantile; df >= 3)."""
    if df < 3:
        raise CouldNotVerify(f'too few degrees of freedom ({df}) for a confidence band')
    z = _Z[level]
    g1 = (z ** 3 + z) / 4
    g2 = (5 * z ** 5 + 16 * z ** 3 + 3 * z) / 96
    g3 = (3 * z ** 7 + 19 * z ** 5 + 17 * z ** 3 - 15 * z) / 384
    return z + g1 / df + g2 / df ** 2 + g3 / df ** 3


@dataclass(frozen=True)
class Band:
    """Slope per hour with its 95% band; n samples, n_eff after the serial-correlation correction."""
    slope: float
    lo: float
    hi: float
    n: int
    n_eff: float

    def overlaps(self, other):
        return self.lo <= other.hi and other.lo <= self.hi


def fit_slope(seconds, values, warmup_s=0.0, level=0.95):
    """OLS slope per hour over samples after the warm-up, with a band widened for lag-1 autocorrelation.

    Samples of a counter are serially correlated, which makes a plain OLS band too narrow; the standard error is
    inflated by sqrt(n / n_eff), n_eff = n (1 - r) / (1 + r), r the lag-1 autocorrelation of the residuals
    (clamped to [0, 0.95]).
    """
    pairs = [(t, v) for t, v in zip(seconds, values) if v is not None and t >= warmup_s]
    n = len(pairs)
    if n < 8:
        raise CouldNotVerify(f'{n} usable sample(s) after warm-up; a slope needs at least 8')
    xs = [t / 3600.0 for t, _ in pairs]
    ys = [float(v) for _, v in pairs]
    mean_x, mean_y = sum(xs) / n, sum(ys) / n
    sxx = sum((x - mean_x) ** 2 for x in xs)
    if sxx == 0:
        raise CouldNotVerify('all samples carry the same timestamp')
    slope = sum((x - mean_x) * (y - mean_y) for x, y in zip(xs, ys)) / sxx
    intercept = mean_y - slope * mean_x
    residuals = [y - (intercept + slope * x) for x, y in zip(xs, ys)]
    ss_res = sum(r * r for r in residuals)
    lag = sum(a * b for a, b in zip(residuals[:-1], residuals[1:]))
    r1 = 0.0 if ss_res == 0 else min(max(lag / ss_res, 0.0), 0.95)
    n_eff = max(n * (1 - r1) / (1 + r1), 4.0)
    se = math.sqrt(ss_res / (n - 2) / sxx) * math.sqrt(n / n_eff)
    half = t_quantile(max(int(n_eff) - 2, 3), level) * se
    return Band(slope, slope - half, slope + half, n, n_eff)


def percentile(values, q):
    """Nearest-rank percentile, q in (0, 100]."""
    if not values:
        raise CouldNotVerify('no values for a percentile')
    ordered = sorted(values)
    rank = max(1, math.ceil(q / 100.0 * len(ordered)))
    return ordered[rank - 1]


GROWTH_WITHIN = 'within-budget'
GROWTH_OVER = 'over-budget'
GROWTH_UNDECIDED = 'undecided'


def judge_growth(measured, control, budget_per_hour):
    """Judge a slope against its budget and the control run.

    - the whole band at or under the budget: within budget. A control that also moved only widens what the
      app is blamed for, so it cannot turn a bounded slope into a failure and is not needed for the claim;
    - the band's lower bound above zero, above the control band and above the budget: over budget, a leak finding
      (section 7 item 3: lower bound above the control band and above zero);
    - anything else - the band straddles the budget, or the growth cannot be told from the system's own churn -
      is undecided: exit 2, neither a finding nor a pass (section 7 item 5, blank-run overlap).
    Returns (state, reason). control may be None when the metric could not be sampled without the app.
    """
    if measured.hi <= budget_per_hour:
        return GROWTH_WITHIN, f'band [{measured.lo:+.2f}, {measured.hi:+.2f}]/h within {budget_per_hour:+.2f}/h'
    if measured.lo > budget_per_hour and measured.lo > 0:
        if control is None:
            return GROWTH_UNDECIDED, 'growth above the budget but no control band for this metric'
        if measured.lo > control.hi:
            return GROWTH_OVER, f'lower bound {measured.lo:+.2f}/h above budget {budget_per_hour:+.2f}/h and control {control.hi:+.2f}/h'
        return GROWTH_UNDECIDED, f'measured band overlaps the control band [{control.lo:+.2f}, {control.hi:+.2f}]/h'
    return GROWTH_UNDECIDED, f'band [{measured.lo:+.2f}, {measured.hi:+.2f}]/h straddles the budget {budget_per_hour:+.2f}/h'
