"""Reproduce merged mixed-BC startup failure and validate aligned dependencies without a device."""
from pathlib import Path
import argparse, os, subprocess, tempfile

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--before-only', action='store_true')
a=p.parse_args()
root=Path(__file__).resolve().parents[1]
cache=Path(os.environ.get('GRADLE_USER_HOME',str(Path.home()/'.gradle')))/'caches/modules-2/files-2.1/org.bouncycastle'
java=Path(os.environ['JAVA_HOME'])/'bin'
def jars(pkix,prov):
    result=[]
    for artifact,version in [('bcpkix-jdk18on',pkix),('bcutil-jdk18on',pkix),('bcprov-jdk18on',prov)]:
        found=list((cache/artifact/version).glob('*/'+artifact+'-'+version+'.jar'))
        if len(found)!=1:raise RuntimeError('Expected one cached Maven artifact: '+artifact+':'+version)
        result.append(str(found[0]))
    return os.pathsep.join(result)
baseline=jars('1.77','1.77');mixed=jars('1.77','1.85.2')
with tempfile.TemporaryDirectory(prefix='mooncast-test-identities-') as temp:
    output=Path(temp)/'classes';output.mkdir()
    sources=[root/'app/src/main/java/com/mooncast/host/HostIdentity.java',root/'tests/HostIdentityCompatibilityTest.java',root/'tests/host_identity_stubs/android/util/Base64.java']
    subprocess.run([java/'javac','--release','17','-cp',baseline,'-d',output,*sources],check=True)
    def run(classpath,mode,directory):
        subprocess.run([java/'java','-cp',str(output)+os.pathsep+classpath,'com.mooncast.host.HostIdentityCompatibilityTest',mode,Path(temp)/directory],check=True)
    run(baseline,'old-1.77','old')
    run(mixed,'expect-mixed-failure','mixed')
    if not a.before_only:
        # Official bc-jdk18on-bom:1.85.2 uses PKIX/util 1.85 and provider 1.85.2.
        aligned=jars('1.85','1.85.2')
        run(aligned,'aligned-fresh','fresh')
        run(aligned,'aligned-existing-1.77','old')
