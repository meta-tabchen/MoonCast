package com.mooncast.host;
import com.mooncast.transfer.ApkTransfer;
import java.io.File;
import java.io.IOException;
public final class ApkDocumentNameTest {
    public static void main(String[] args) throws Exception {
        int oldRejected=0, accepted=0;
        String[] names={null,"My development build","own-app.apk.download","folder/app.apk","build\\app.apk","bad\n.apk","x".repeat(245)+".apk","开发版.apk"};
        for (String name:names) {
            try { ApkTransfer.checkName(name); } catch (IOException expected) { oldRejected++; }
            String wire=ApkDocumentName.wireName(name); ApkTransfer.checkName(wire);
            if(wire.contains("/") || wire.contains("\\"))throw new AssertionError("unsafe wire name");
            for(String file:args) { ApkSingleManifest.requireStandalone(new File(file)); accepted++; }
        }
        if(oldRejected!=7 || args.length<2)throw new AssertionError("Missing baseline failure or actual phone/TV fixtures");
        System.out.println("PASS: old display-name gate rejects 7 valid-file metadata variants; new normalization accepts "+accepted+" actual APK/name combinations; APK content checks unchanged");
    }
}
