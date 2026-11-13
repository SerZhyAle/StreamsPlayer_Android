"""CHECK-VERDICT plumbing for every rig script: exit 0 / 1 / 2 and one verdict line, last on stdout.

0 within budget (or collected), 1 inspected and defective, 2 could not verify - never a pass.
A script documents only the codes it can reach; a fixture forces each of them.
"""
import sys

PASS, FAIL, COULD_NOT_VERIFY = 0, 1, 2
WORDS = {PASS: 'PASS', FAIL: 'FAIL', COULD_NOT_VERIFY: 'COULD NOT VERIFY'}


class CouldNotVerify(Exception):
    """An input or the instrument itself cannot be trusted: the run exits 2 and never produces a finding."""


def verdict_line(subject, code, detail=''):
    word = WORDS[code]
    return f'{subject}: {word} ({detail})' if detail else f'{subject}: {word}'


def run(subject, main):
    """Run main() -> (code, detail); print the verdict line last, on every path (rule 11), and exit with the code."""
    try:
        code, detail = main()
    except CouldNotVerify as error:
        print(f'reason: {error}')
        code, detail = COULD_NOT_VERIFY, str(error)
    except Exception as error:  # noqa: BLE001 - the verdict line is owed even when the script dies
        print(f'reason: internal error {type(error).__name__}: {error}')
        code, detail = COULD_NOT_VERIFY, f'internal error: {type(error).__name__}: {error}'
    line = verdict_line(subject, code, detail)
    sys.stdout.flush()
    print(line)
    sys.stdout.flush()
    sys.exit(code)
