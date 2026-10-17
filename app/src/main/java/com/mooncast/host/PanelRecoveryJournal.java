package com.mooncast.host;
/** Persist before a physical side effect. A timeout or user acknowledgement never clears it. */
final class PanelRecoveryJournal {
 interface Storage { boolean read(String key); boolean write(String key,boolean value); }
 private final Storage store;private boolean failed;
 PanelRecoveryJournal(Storage store){this.store=store;failed=store.read("quarantined")||store.read("armed");}
 boolean blocked(){return failed;}
 boolean arm(){if(failed)return false;if(!store.write("armed",true)){failed=true;return false;}return true;}
 boolean restored(){if(failed)return false;if(!store.write("armed",false)){failed=true;return false;}return true;}
 void quarantine(){failed=true;store.write("quarantined",true);/* armed remains durable even if this write fails */}
}
