"""Entry point of the S0025 measurement rig. See tools/audit/README.md.

    python tools/audit/rig.py selftest [--serial emulator-5554]
    python tools/audit/rig.py sample|frames|latency|evaluate|session ...

Exit codes (CHECK-VERDICT): 0 collected / within budget, 1 inspected and defective, 2 could not verify.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from auditrig.cli import main  # noqa: E402

if __name__ == '__main__':
    main()
