#!/usr/bin/env python3
import os,subprocess,tempfile
from pathlib import Path
r=Path(__file__).resolve().parents[1];j=Path(os.environ['JAVA_HOME'])/'bin'
with tempfile.TemporaryDirectory() as d:
 p=Path(d)/'LocaleTest.java';p.write_text('''import com.mooncast.locale.LocalePolicy; public class LocaleTest {public static void main(String[] a){int checks=0;for(String v:new String[]{null,"","auto","system","fr","../zh","EN","zh-CN"}){if(!"system".equals(LocalePolicy.valid(v)))throw new AssertionError(v);checks++;}for(String v:new String[]{"en","zh"}){if(!v.equals(LocalePolicy.valid(v)))throw new AssertionError();checks++;}for(String v:new String[]{null,"en","fr","de","ar","ja"}){if(!"en".equals(LocalePolicy.resourceLanguage(v)))throw new AssertionError();checks++;}if(!"zh".equals(LocalePolicy.resourceLanguage("zh")))throw new AssertionError();System.out.println("PASS "+(checks+1)+" locale validation and unsupported-language fallback checks");}}''')
 subprocess.run([j/'javac','-encoding','UTF-8','-d',d,r/'locale-core/src/main/java/com/mooncast/locale/LocalePolicy.java',p],check=True)
 subprocess.run([j/'java','-cp',d,'LocaleTest'],check=True)
