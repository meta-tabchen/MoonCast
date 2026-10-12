#!/usr/bin/env python3
"""Bilingual resource parity, valid XML and format-argument contract; no UI/interoperability claim."""
from pathlib import Path
import re,xml.etree.ElementTree as ET
R=Path(__file__).resolve().parents[1]
def resources(module,qualifier):
 result={}
 for file in sorted((R/module/'src/main/res'/qualifier).glob('*.xml')):
  for node in ET.parse(file).getroot():
   if node.tag not in {'string','plurals','string-array'} or node.get('translatable')=='false':continue
   key=node.get('name');assert key not in result,(file,key,'duplicate')
   result[key]=(node.tag,''.join(node.itertext()),node.get('formatted')!='false')
 return result
def formats(text):
 return sorted(re.findall(r'%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]',text.replace('%%','')))
count=0
for module in ['app','tv','unified','locale-core']:
 en,zh=resources(module,'values'),resources(module,'values-zh')
 assert en.keys()==zh.keys(),(module,'missing translations',sorted(en.keys()-zh.keys()),sorted(zh.keys()-en.keys()))
 for key,(kind,text,formatted) in en.items():
  assert kind==zh[key][0] and formatted==zh[key][2] and (not formatted or formats(text)==formats(zh[key][1])),(module,key,'format mismatch')
  assert text.strip() and zh[key][1].strip(),(module,key,'empty translation')
 count+=len(en)
 print(f'{module}: PASS {len(en)} English/Chinese entries')
print(f'PASS {count} bilingual entries and format contracts; external diagnostic details are not translated by this check')
