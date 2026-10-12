#!/usr/bin/env python3
"""Test pinned native queue source plus reviewed overlays, without Android/network/audio.

Crypto is a plaintext test stub. Queue/normalizer function bodies are extracted exactly
from the verified source archive (baseline) or temporary patch application (candidate).
Use --sanitize for ASan+UBSan. Leak detection is disabled only if explicitly configured
in the environment; some executor ptrace environments cannot run LeakSanitizer.
"""
from pathlib import Path
import hashlib, json, os, re, subprocess, sys, tarfile, tempfile
root=Path(__file__).resolve().parents[1];native=root/'native/airplay'
archive=native/'upstream-source.tar.gz'
manifest=json.loads((native/'prebuilt-manifest.json').read_text())
assert hashlib.sha256(archive.read_bytes()).hexdigest()==manifest['sources']['upstream-source.tar.gz']
fixture=(native/'tests/rtp_audio_transition_fixture.c').read_text()
prefix='android-airplay-server/app/src/main/cpp/third_party/UxPlay/lib/'
def function(source,name):
    match=re.search(r'\n(?:static )?(?:unsigned short|short|int|void \*|void)\s*\n?'+name+r'\(',source)
    assert match,name
    start=match.start();brace=source.index('{',match.end());depth=1;end=brace+1
    while depth:
        depth+=(source[end]=='{')-(source[end]=='}');end+=1
    return source[start:end]
def flush_dispatch(rtp):
    marker=rtp.index('/* Handle flush if requested */')
    start=rtp.index('if (flush != NO_FLUSH)',marker);brace=rtp.index('{',start);depth=1;end=brace+1
    while depth:
        depth+=(rtp[end]=='{')-(rtp[end]=='}');end+=1
    return rtp[start:end]
def harness(buffer,rtp,baseline):
    window=re.search(r'^#define RAOP_BUFFER_LENGTH (\d+)$',buffer,re.M)
    assert window and int(window.group(1))==256,'Pinned packet-window contract changed; review fixture expectations'
    dispatch=flush_dispatch(rtp)
    no_flush=re.search(r'^#define NO_FLUSH \(([-0-9]+)\)$',rtp,re.M)
    assert no_flush

    structs=buffer[buffer.index('typedef struct {'):buffer.index('\nraop_buffer_t *')]
    return '''#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <stdio.h>
#include <stdint.h>
#define RAOP_BUFFER_LENGTH 256
#define RAOP_PACKET_LEN 2048
typedef void logger_t;typedef void aes_ctx_t;
typedef void (*raop_resend_cb_t)(void *, unsigned short, unsigned short);
#define LOGGER_DEBUG 0
#define logger_log(...) ((void)0)
typedef struct raop_buffer_s raop_buffer_t;
'''+structs+'''
void raop_buffer_flush(raop_buffer_t *,int);
static unsigned short byteutils_get_short_be(unsigned char*p,int i){return p[i]*256+p[i+1];}
static uint32_t byteutils_get_int_be(unsigned char*p,int i){return ((uint32_t)p[i]<<24)|((uint32_t)p[i+1]<<16)|(p[i+2]<<8)|p[i+3];}
static int raop_buffer_decrypt(raop_buffer_t*b,unsigned char*data,unsigned char*out,unsigned int n,unsigned int*outlen){memcpy(out,data+12,n);*outlen=n;return 1;}
'''+ '\n'.join(function(buffer,n) for n in ['seqnum_cmp','raop_buffer_enqueue','raop_buffer_dequeue','raop_buffer_flush','raop_buffer_handle_resends'])+('' if baseline else function(rtp,'raop_rtp_payload_length'))+'''
#define NO_FLUSH '''+no_flush.group(1)+'''
typedef struct {raop_buffer_t *buffer;struct {void(*audio_flush)(void*);void*cls;}callbacks;} raop_rtp_t;
static unsigned flush_notifications;
static void flush_callback(void*unused){flush_notifications++;}
static void dispatch_flush(raop_buffer_t*buffer,int flush){
    raop_rtp_t owner={.buffer=buffer,.callbacks={.audio_flush=flush_callback,.cls=NULL}};
    raop_rtp_t *raop_rtp=&owner;
'''+dispatch+'''
}
'''+fixture
with tempfile.TemporaryDirectory(prefix='airplay-rtp-regression-') as temp:
    work=Path(temp);(work/'lib').mkdir()
    with tarfile.open(archive) as tar:
        for name in ['raop_buffer.c','raop_rtp.c']:
            (work/'lib'/name).write_bytes(tar.extractfile(prefix+name).read())
    old_buffer=(work/'lib/raop_buffer.c').read_text();old_rtp=(work/'lib/raop_rtp.c').read_text()
    patches=sorted((native/'patches').glob('*.patch'))
    assert len(patches)==2,'Expected explicit reviewed patch set'
    for patch in patches:subprocess.run(['patch','--batch','--fuzz=0','-p1','-i',str(patch)],cwd=work,check=True)
    new_buffer=(work/'lib/raop_buffer.c').read_text();new_rtp=(work/'lib/raop_rtp.c').read_text()
    assert new_rtp.count('raop_rtp_payload_length(')==3,'Normalizer must cover both data and resend paths'
    assert 'raop_buffer_flush(raop_rtp->buffer, flush);' in flush_dispatch(new_rtp)
    for name,buffer,rtp,baseline in [('baseline',old_buffer,old_rtp,True),('candidate',new_buffer,new_rtp,False)]:
        source=work/(name+'.c');source.write_text(harness(buffer,rtp,baseline))
        cmd=[os.environ.get('CC','cc'),'-std=c11','-O1','-g']
        if baseline:cmd+=['-DBASELINE']
        if '--sanitize' in sys.argv:cmd+=['-fsanitize=address,undefined']
        subprocess.run(cmd+[str(source),'-o',str(work/name)],check=True)
        subprocess.run([str(work/name)],check=True)
print('PASS archive-pinned native RTP transition regression; no binary/provenance input modified.')
