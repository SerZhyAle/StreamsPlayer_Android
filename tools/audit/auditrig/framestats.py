"""Frame-clock instrument (S0025 section 7 item 2): `dumpsys gfxinfo <pkg> framestats` across a scripted action.

The device keeps only the last 120 frames per window, so a long window is polled and the polls are merged by
IntendedVsync. Per frame, in ns on the device's monotonic clock:
  main-thread time  = SyncQueued    - IntendedVsync   (vsync scheduled -> UI thread hands the frame to RenderThread;
                                                       a main thread blocked elsewhere shows up here as a late start)
  frame time        = FrameCompleted - IntendedVsync
  missed deadline   = FrameCompleted > FrameDeadline
The "main thread wait" number of the audit is the longest main-thread time in the window.

A frame whose own timestamps are not in timeline order (IntendedVsync <= HandleInputStart <= SyncQueued <=
FrameCompleted) is a corrupt reading, not a subject finding: seen live on 2026-10-24 on the API 35 audit image as
one row whose FrameCompleted sat 65 s before its vsync right after a `gfxinfo reset`. Corrupt frames are dropped
and counted; more than CORRUPT_LIMIT dropped means the framestats columns are not what this parser expects.
"""
import time

from . import parsers
from .verdict import CouldNotVerify

NS_PER_MS = 1_000_000.0
CORRUPT_LIMIT = 0.10


class FrameCollector:
    def __init__(self, adb, package):
        self.adb = adb
        self.package = package
        self.frames = {}

    def reset(self):
        self.adb.shell(f'dumpsys gfxinfo {self.package} reset')
        self.frames = {}

    def poll(self):
        text = self.adb.shell(f'dumpsys gfxinfo {self.package} framestats')
        try:
            for frame in parsers.parse_gfx_framestats(text):
                self.frames[frame['intended_vsync']] = frame
        except parsers.Unreadable as error:
            raise CouldNotVerify(f'framestats unreadable: {error}') from error
        return len(self.frames)

    def run_window(self, duration_s, poll_s=1.0, sleep=time.sleep, clock=time.monotonic):
        end = clock() + duration_s
        while True:
            self.poll()
            if clock() >= end:
                break
            sleep(poll_s)
        return self.metrics()

    def metrics(self):
        return frame_metrics(list(self.frames.values()))


def frame_metrics(frames):
    """Frames -> longest main-thread time, longest frame, missed deadlines, corrupt-row count. No frames left after
    the corrupt drop is COULD NOT VERIFY: a window in which nothing was drawn observed nothing (a static screen is
    not a fast one)."""
    kept = [f for f in frames if f['intended_vsync'] <= f['handle_input_start'] <= f['sync_queued'] <= f['completed']]
    dropped = len(frames) - len(kept)
    if dropped > CORRUPT_LIMIT * len(frames):
        raise CouldNotVerify(f'{dropped} of {len(frames)} frames are out of timeline order: the framestats columns are not what this parser expects')
    if not kept:
        raise CouldNotVerify('no frames in the window (nothing was drawn, so nothing was measured)')
    main = [(f['sync_queued'] - f['intended_vsync']) / NS_PER_MS for f in kept]
    total = [(f['completed'] - f['intended_vsync']) / NS_PER_MS for f in kept]
    with_deadline = [f for f in kept if f['deadline'] is not None]
    missed = len([f for f in with_deadline if f['completed'] > f['deadline']])
    return {
        'frames': len(kept),
        'dropped_frames': dropped,
        'longest_main_thread_ms': round(max(main), 3),
        'longest_frame_ms': round(max(total), 3),
        'missed_deadlines': missed,
        'missed_fraction': round(missed / len(with_deadline), 4) if with_deadline else None,
    }
