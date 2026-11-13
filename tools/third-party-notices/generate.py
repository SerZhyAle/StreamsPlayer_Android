"""Preserve Maven publisher declarations and embedded runtime notices in Android assets.

Resolve the inventory first with runtime-inventory.gradle; see README.md.
Only local Gradle artifacts are read. No publisher metadata is inferred.
"""
import argparse
from pathlib import Path
import xml.etree.ElementTree as ET,zipfile,io,hashlib
root=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser()
parser.add_argument('--inventory-dir', type=Path, default=root/'temp/third-party-notices')
args=parser.parse_args()
for module in ['wear','phone']:
 rows=sorted(set(tuple(line.split('\t')) for line in (args.inventory_dir/f'{module}-runtime.tsv').read_text().splitlines()))
 entries=[];texts={}
 for group,name,version,artifact in rows:
  p=Path(artifact);poms=list(p.parent.parent.glob('*/*.pom'));licenses=[]
  if poms:
   doc=ET.parse(poms[0]).getroot();ns={'m':'http://maven.apache.org/POM/4.0.0'}
   licenses=[(x.findtext('m:name',default='',namespaces=ns),x.findtext('m:url',default='',namespaces=ns)) for x in doc.findall('m:licenses/m:license',ns)]
  entries.append(f'{group}:{name}:{version}\n'+('  '+ '; '.join(n+' ('+u+')' for n,u in licenses) if licenses else '  License metadata: see upstream Maven POM and included notices below.'))
  def collect(z):
   for entry in z.namelist():
    if entry.endswith('/') or entry.endswith('.json'):continue
    base=entry.rsplit('/',1)[-1].lower()
    if 'license' in base or 'notice' in base:
     content=z.read(entry).decode('utf-8',errors='replace').strip();key=hashlib.sha256(content.encode()).hexdigest()
     if content:
      if key not in texts:texts[key]=[content,[]]
      texts[key][1].append(f'{group}:{name}:{version} / {entry}')
   if 'classes.jar' in z.namelist():
    with zipfile.ZipFile(io.BytesIO(z.read('classes.jar'))) as nested:collect(nested)
  with zipfile.ZipFile(p) as archive:collect(archive)
 notice='StreamsPlayer third-party notices\n\nResolved store release runtime components. Versions come from Gradle resolution.\nPublisher POM license declarations and embedded license/notice texts are preserved below.\nGoogle Play services components use the Android SDK license stated in their POM.\n\n'+'\n\n'.join(entries)+'\n\n'
 for content,owners in texts.values():notice+='='*72+'\n'+'\n'.join(sorted(set(owners)))+'\n\n'+content+'\n\n'
 out=root/module/'src/main/assets/THIRD-PARTY-NOTICES.txt';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(notice,encoding='utf-8');print(module,len(rows),'components;',len(texts),'unique embedded notices;',out.stat().st_size,'bytes')
