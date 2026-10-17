"""Pure, fail-closed policy helpers shared by release packaging checks."""
import pathlib,re

def build_id(xmltree):
    values=[];lines=xmltree.splitlines()
    for index,line in enumerate(lines):
        match=re.match(r'^(\s*)E: meta-data(?:\s|$)',line)
        if not match:continue
        indent=len(match[1]);attributes={}
        for child in lines[index+1:]:
            if child.strip() and len(child)-len(child.lstrip())<=indent:break
            attr=re.search(r'A: android:(name|value)\([^)]*\)="([^"]*)"',child)
            if attr:attributes[attr[1]]=attr[2]
        if attributes.get('name')=='com.mooncast.BUILD_ID':values.append(attributes.get('value'))
    if len(values)!=1 or not re.fullmatch('[0-9a-f]{40}',values[0] or ''):raise ValueError('one exact BUILD_ID metadata value required')
    return values[0]

def legal_paths(inventory,webrtc,qr):
    result=set()
    for name in inventory:
        base=pathlib.PurePosixPath(name).name.upper()
        if base.startswith(('LICENSE','LICENCE','NOTICE','COPYING','COPYRIGHT','PATENTS','THIRD_PARTY','THIRD-PARTY')) or '/licenses/' in name.lower():result.add(name)
    result.update(row['path'] for row in webrtc['licenses'])
    for row in qr['bundled']:
        if row['path'].startswith('app/src/main/assets/') and any(word in pathlib.PurePosixPath(row['path']).name.upper() for word in ('LICENSE','LICENCE','NOTICE','COPYING','COPYRIGHT','PATENTS','THIRD-PARTY')):result.add(row['path'])
    if not result<=set(inventory):raise ValueError('locked legal asset missing from source')
    if not {'LICENSE','THIRD_PARTY_NOTICES.md'}<=result:raise ValueError('root notices missing')
    return sorted(result)
