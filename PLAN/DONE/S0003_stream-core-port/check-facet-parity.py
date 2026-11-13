from pathlib import Path
import os
import re

source = Path(os.environ.get('STREAMSPLAYER_FMS_ROOT', 'P:/ANDROID/FastMediaSorter_mob_v2'))
publisher = (source / 'scripts/streams/modules/StreamPublisher.Facets.ps1').read_text(encoding='utf-8')
phone = Path('phone/src/main/java/com/sza/streamsplayer/domain/streams/facets/StreamFacetTables.kt').read_text(encoding='utf-8')
pairs = {
    'FacetIso6391': 'ISO_639_1_TABLE',
    'FacetIso6393Extension': 'ISO_639_3_EXTENSION_TABLE',
    'FacetLanguageAliases': 'LANGUAGE_ALIAS_TABLE',
    'FacetAssignedCountries': 'ASSIGNED_COUNTRY_TABLE',
    'FacetCountryAliases': 'COUNTRY_ALIAS_TABLE',
}
for variable, constant in pairs.items():
    script_match = re.search(r"\$script:" + variable + r" = @'\n(.*?)\n'@", publisher, re.S)
    kotlin_match = re.search(r'const val ' + constant + r' = """(.*?)"""', phone, re.S)
    assert script_match and kotlin_match, f'Missing declaration: {variable} / {constant}'
    script_rows = [row.strip() for row in script_match[1].splitlines() if row.strip()]
    kotlin_rows = [row.strip() for row in kotlin_match[1].splitlines() if row.strip()]
    assert script_rows == kotlin_rows, f'Publisher mismatch: {constant}'
    print(f'PASS {constant}: {len(kotlin_rows)} rows equal the publisher')
print('PASS: all 5 facet tables match; no empty traversal.')
