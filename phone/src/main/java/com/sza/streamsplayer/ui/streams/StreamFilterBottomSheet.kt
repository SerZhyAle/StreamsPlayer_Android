package com.sza.streamsplayer.ui.streams

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sza.streamsplayer.R
import com.sza.streamsplayer.ui.common.help.HelpKeys
import com.sza.streamsplayer.ui.common.help.HelpOpenManager
import com.sza.streamsplayer.ui.common.help.HelpTooltipManager
import com.sza.streamsplayer.ui.common.help.HelpSurfaceIds
import com.sza.streamsplayer.databinding.SheetStreamFilterBinding

class StreamFilterBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: StreamsViewModel by activityViewModels()

    private var _binding: SheetStreamFilterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = SheetStreamFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        (dialog as? com.google.android.material.bottomsheet.BottomSheetDialog)?.behavior?.state =
            com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        HelpKeys.install(dialog, HelpSurfaceIds.FILTER_AND_SORT)
        binding.btnFilterHelp.setOnClickListener { HelpOpenManager.open(requireContext(), HelpSurfaceIds.FILTER_AND_SORT) }
        androidx.appcompat.widget.TooltipCompat.setTooltipText(binding.btnFilterHelp, HelpTooltipManager.tooltipText(requireContext()))
        val context = requireContext()
        val currentFilter = viewModel.filter.value
        val facets = viewModel.state.value.facets
        val collections = viewModel.state.value.collections
        val locale = resources.configuration.locales[0]
        val collectionNames = listOf(getString(R.string.all_collections)) + collections.map {
            com.sza.streamsplayer.ui.streams.helpers.StreamCollectionNameResolver.resolve(it.id, it.namesJson, locale)
        }
        binding.spinnerCollection.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, collectionNames).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerCollection.setSelection(collections.indexOfFirst { it.id == currentFilter.collectionId } + 1)

        // Categories
        val allCategories = listOf(getString(R.string.filter_all)) + facets.categories
        val categoryAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, allCategories)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCategory.adapter = categoryAdapter
        val catIndex = if (currentFilter.category != null) facets.categories.indexOf(currentFilter.category) + 1 else 0
        if (catIndex >= 0) binding.spinnerCategory.setSelection(catIndex)

        // Languages
        val allLanguages = listOf(getString(R.string.filter_all)) + facets.languages
        val languageAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, allLanguages)
        languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLanguage.adapter = languageAdapter
        val langIndex = if (currentFilter.language != null) facets.languages.indexOf(currentFilter.language) + 1 else 0
        if (langIndex >= 0) binding.spinnerLanguage.setSelection(langIndex)

        // Countries
        val allCountries = listOf(getString(R.string.filter_all)) + facets.countries
        val countryAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, allCountries)
        countryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCountry.adapter = countryAdapter
        val countryIdx = if (currentFilter.country != null) facets.countries.indexOf(currentFilter.country) + 1 else 0
        if (countryIdx >= 0) binding.spinnerCountry.setSelection(countryIdx)

        // Sort options
        val sortOptions = listOf(
            getString(R.string.sort_name) to StreamsViewModel.SortMode.NAME,
            getString(R.string.sort_topic) to StreamsViewModel.SortMode.TOPIC,
            getString(R.string.sort_language) to StreamsViewModel.SortMode.LANGUAGE,
            getString(R.string.sort_country) to StreamsViewModel.SortMode.COUNTRY,
            getString(R.string.sort_recent) to StreamsViewModel.SortMode.RECENT,
        )
        val sortAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, sortOptions.map { it.first })
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSort.adapter = sortAdapter
        val sortIdx = sortOptions.indexOfFirst { it.second == currentFilter.sort }.coerceAtLeast(0)
        binding.spinnerSort.setSelection(sortIdx)

        binding.cbPinnedOnly.isChecked = currentFilter.pinnedOnly

        binding.btnClearFilters.setOnClickListener {
            viewModel.onClearFilters()
            dismiss()
        }

        binding.btnApplyFilters.setOnClickListener {
            val selectedCategory = if (binding.spinnerCategory.selectedItemPosition > 0) {
                facets.categories[binding.spinnerCategory.selectedItemPosition - 1]
            } else {
                null
            }
            val selectedLanguage = if (binding.spinnerLanguage.selectedItemPosition > 0) {
                facets.languages[binding.spinnerLanguage.selectedItemPosition - 1]
            } else {
                null
            }
            val selectedCountry = if (binding.spinnerCountry.selectedItemPosition > 0) {
                facets.countries[binding.spinnerCountry.selectedItemPosition - 1]
            } else {
                null
            }
            val selectedSort = sortOptions[binding.spinnerSort.selectedItemPosition].second

            val collectionId = collections.getOrNull(binding.spinnerCollection.selectedItemPosition - 1)?.id
            viewModel.onApplyFilter(
                currentFilter.copy(
                    category = selectedCategory,
                    language = selectedLanguage,
                    country = selectedCountry,
                    pinnedOnly = binding.cbPinnedOnly.isChecked,
                    sort = selectedSort,
                    collectionId = collectionId,
                    collectionMemberOrder = if (collectionId == currentFilter.collectionId) currentFilter.collectionMemberOrder else emptyMap(),
                    collectionOrderApplied = collectionId != null && selectedSort == currentFilter.sort,
                ),
            )
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "StreamFilterBottomSheet"
    }
}
