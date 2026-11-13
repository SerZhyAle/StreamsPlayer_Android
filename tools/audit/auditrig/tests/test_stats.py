import unittest

from auditrig import stats
from auditrig.stats import Band
from auditrig.tests.helpers import series
from auditrig.verdict import CouldNotVerify

SECONDS = [i * 5 for i in range(720)]  # one hour at the sampler's 5 s


class SlopeTests(unittest.TestCase):
    def test_t_quantile_matches_the_published_table(self):
        self.assertAlmostEqual(stats.t_quantile(30), 2.0423, delta=0.005)
        self.assertAlmostEqual(stats.t_quantile(10), 2.2281, delta=0.02)
        self.assertAlmostEqual(stats.t_quantile(120), 1.9799, delta=0.002)
        with self.assertRaises(CouldNotVerify):
            stats.t_quantile(2)

    def test_an_exact_line_has_an_exact_slope_and_a_tiny_band(self):
        band = stats.fit_slope(SECONDS, series(SECONDS, 1000, per_hour=4.0))
        self.assertAlmostEqual(band.slope, 4.0, places=6)
        self.assertLess(band.hi - band.lo, 1e-6)

    def test_a_noisy_ramp_band_contains_the_true_slope_about_nineteen_times_in_twenty(self):
        misses = 0
        for seed in range(1, 61):
            band = stats.fit_slope(SECONDS, series(SECONDS, 1000, per_hour=40.0, noise=3.0, seed=seed))
            misses += 0 if band.lo <= 40.0 <= band.hi else 1
        self.assertLessEqual(misses, 7, 'a 95% band may miss the truth about one run in twenty (expected 3 of 60)')

    def test_flat_noise_band_contains_zero_in_nearly_every_seed(self):
        misses = 0
        for seed in range(1, 61):
            band = stats.fit_slope(SECONDS, series(SECONDS, 1000, noise=3.0, seed=seed))
            misses += 0 if band.lo <= 0 <= band.hi else 1
        self.assertLessEqual(misses, 7, 'a 95% band may miss the truth about one run in twenty (expected 3 of 60)')

    def test_warmup_samples_are_excluded(self):
        values = [5000.0 if t < 600 else 1000.0 for t in SECONDS]
        self.assertAlmostEqual(stats.fit_slope(SECONDS, values, warmup_s=600).slope, 0.0, places=6)
        self.assertGreater(abs(stats.fit_slope(SECONDS, values).slope), 100)

    def test_too_few_samples_is_not_a_slope(self):
        with self.assertRaises(CouldNotVerify):
            stats.fit_slope([0, 5, 10], [1, 2, 3])
        with self.assertRaises(CouldNotVerify):
            stats.fit_slope(SECONDS, [None] * len(SECONDS))

    def test_serial_correlation_widens_the_band(self):
        import random
        rng = random.Random(3)
        walk, level = [], 0.0
        for _ in SECONDS:
            level = 0.97 * level + rng.gauss(0, 1)
            walk.append(1000 + level)
        correlated = stats.fit_slope(SECONDS, walk)
        independent = stats.fit_slope(SECONDS, [1000 + rng.gauss(0, 1) for _ in SECONDS])
        self.assertLess(correlated.n_eff, correlated.n / 5)
        self.assertGreater(correlated.hi - correlated.lo, independent.hi - independent.lo)

    def test_percentile_is_nearest_rank(self):
        self.assertEqual(stats.percentile([1, 2, 3, 4, 5, 6, 7, 8, 9, 10], 50), 5)
        self.assertEqual(stats.percentile([1, 2, 3, 4, 5, 6, 7, 8, 9, 10], 95), 10)
        with self.assertRaises(CouldNotVerify):
            stats.percentile([], 50)


class GrowthJudgementTests(unittest.TestCase):
    def band(self, lo, hi):
        return Band((lo + hi) / 2, lo, hi, 100, 50.0)

    def test_a_band_under_the_budget_is_within_budget_whatever_the_control_did(self):
        self.assertEqual(stats.judge_growth(self.band(-1, 3), None, 5)[0], stats.GROWTH_WITHIN)
        self.assertEqual(stats.judge_growth(self.band(-1, 3), self.band(10, 20), 5)[0], stats.GROWTH_WITHIN)

    def test_a_band_clear_of_budget_zero_and_control_is_a_leak(self):
        state, reason = stats.judge_growth(self.band(8, 12), self.band(-1, 1), 5)
        self.assertEqual(state, stats.GROWTH_OVER)
        self.assertIn('above budget', reason)

    def test_growth_inside_the_control_band_is_undecided(self):
        state, reason = stats.judge_growth(self.band(8, 12), self.band(7, 15), 5)
        self.assertEqual(state, stats.GROWTH_UNDECIDED)
        self.assertIn('control band', reason)

    def test_growth_with_no_control_cannot_be_called_a_leak(self):
        self.assertEqual(stats.judge_growth(self.band(8, 12), None, 5)[0], stats.GROWTH_UNDECIDED)

    def test_a_band_straddling_the_budget_is_undecided(self):
        self.assertEqual(stats.judge_growth(self.band(2, 9), self.band(-1, 1), 5)[0], stats.GROWTH_UNDECIDED)

    def test_a_zero_budget_needs_the_whole_band_at_or_under_zero(self):
        self.assertEqual(stats.judge_growth(self.band(-2, 0), None, 0)[0], stats.GROWTH_WITHIN)
        self.assertEqual(stats.judge_growth(self.band(-1, 2), self.band(-1, 1), 0)[0], stats.GROWTH_UNDECIDED)


if __name__ == '__main__':
    unittest.main()
