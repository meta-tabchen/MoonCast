/* SPDX-License-Identifier: GPL-3.0-only */
/* Included after exact production queue functions; decryption is a generated test stub. */
static int packet(raop_buffer_t*b,unsigned seq,int kind){
 unsigned char p[64]={0};p[0]=0x80;p[1]=0x60;p[2]=seq>>8;p[3]=seq;p[12]=42;
 int len=13;unsigned char ct=8;
 if(kind==1)len=12;
 if(kind==2){len=16;p[12]=0x00;p[13]=0x68;p[14]=0x34;p[15]=0x00;}
 if(kind==3){len=16;p[12]=0x00;p[13]=0x68;p[14]=0x34;p[15]=0x01;}
 if(kind==4)len=11;
 if(kind==5){len=44;ct=2;}
 if(kind==6){len=44;ct=8;}
 #ifdef BASELINE
 if(kind==5)return 0; /* Exact existing ct2/44-byte RTP input classification. */
 return raop_buffer_enqueue(b,p,len,1);
#else
 return raop_buffer_enqueue(b,p,raop_rtp_payload_length(p,len,ct),1);
#endif
}
static unsigned payload_len;static unsigned char payload_first;
static int get(raop_buffer_t*b){unsigned int len=0;uint32_t ts;unsigned short seq;void*p=raop_buffer_dequeue(b,&len,&ts,&seq,0);if(!p)return -1;payload_len=len;payload_first=((unsigned char*)p)[0];free(p);assert(len>0);return seq;}
static void init(raop_buffer_t*b,unsigned seq){memset(b,0,sizeof(*b));b->is_empty=1;assert(packet(b,seq,0)==1);assert(get(b)==(int)seq);}

static unsigned resend_first,resend_count;
static void resend(void*unused,unsigned short first,unsigned short count){resend_first=first;resend_count=count;}
int main(){
#ifdef BASELINE
 for(int kind=1;kind<=5;kind++){
  if(kind==3||kind==4)continue;
  raop_buffer_t old;init(&old,1000);assert(packet(&old,1001,kind)==0);
  int delivered=-1;unsigned count=0;
  for(unsigned seq=1002;seq<1500;seq++){assert(packet(&old,seq,0)==1);count++;delivered=get(&old);if(delivered>=0)break;}
  assert(delivered==1002&&count==256);raop_buffer_flush(&old,-1);
  printf("BASELINE reproduced: marker kind%d causes256-packet stall without FLUSH.\n",kind);
 }
 raop_buffer_t ignored;init(&ignored,1000);dispatch_flush(&ignored,1020);assert(flush_notifications==1);
 unsigned waited=0;int delivered=-1;
 for(unsigned seq=1020;seq<1500;seq++){assert(packet(&ignored,seq,0)==1);waited++;delivered=get(&ignored);if(delivered>=0)break;}
 assert(delivered==1020&&waited==256);raop_buffer_flush(&ignored,-1);
 puts("BASELINE actual FLUSH dispatch reproduced256-packet stall after explicit boundary.");
 return 0;
#else
 for(int kind=1;kind<=5;kind++){if(kind==3||kind==4)continue;
  raop_buffer_t b;init(&b,1000);assert(packet(&b,1001,kind)==1);assert(packet(&b,1001,kind)==0);
  assert(packet(&b,1002,0)==1);assert(get(&b)==1002);assert(payload_len==1&&payload_first==42);assert(packet(&b,1001,kind)==0);assert(get(&b)==-1);raop_buffer_flush(&b,-1);
 }
 // Missing actual1001 must still wait even though1002 is an explicit marker.
 raop_buffer_t b;init(&b,1000);assert(packet(&b,1002,2)==1);assert(packet(&b,1003,0)==1);assert(get(&b)==-1);
 resend_first=resend_count=0;raop_buffer_handle_resends(&b,resend,NULL);assert(resend_first==1001&&resend_count==1);
 assert(packet(&b,1001,0)==1);assert(get(&b)==1001);assert(get(&b)==1003);assert(get(&b)==-1);raop_buffer_flush(&b,-1);
 // Wrap and duplicate/later marker do not reset the sequence epoch.
 init(&b,65534);assert(packet(&b,65535,1)==1);assert(packet(&b,0,2)==1);assert(packet(&b,1,0)==1);assert(get(&b)==1);
 assert(packet(&b,65535,1)==0);assert(packet(&b,0,2)==0);raop_buffer_flush(&b,-1);
 // Similar-looking encrypted payload is not a marker; malformed short packet does not fill a hole.
 init(&b,1000);assert(packet(&b,1001,3)==1);assert(get(&b)==1001);assert(packet(&b,1002,4)==-1);
 assert(packet(&b,1003,0)==1);assert(get(&b)==-1);assert(packet(&b,1002,0)==1);assert(get(&b)==1002);assert(get(&b)==1003);raop_buffer_flush(&b,-1);
 // Full marker window boundedly advances; later actual sound is immediate.
 init(&b,1000);for(int i=1;i<=256;i++)assert(packet(&b,1000+i,2)==1);assert(get(&b)==-1);
 assert(packet(&b,1257,0)==1);assert(get(&b)==1257);raop_buffer_flush(&b,-1);
 // Marker prefix before sync/first audio; no fictitious packet-loss wait.
 memset(&b,0,sizeof b);b.is_empty=1;assert(packet(&b,42,2)==1);assert(packet(&b,43,0)==1);assert(get(&b)==43);raop_buffer_flush(&b,-1);
 // Existing FLUSH API semantics: explicit next_seq is inclusive; previous packet is late.
 init(&b,1000);unsigned notifications=flush_notifications;dispatch_flush(&b,NO_FLUSH);assert(flush_notifications==notifications);dispatch_flush(&b,1020);assert(flush_notifications==notifications+1);assert(packet(&b,1019,0)==0);assert(packet(&b,1020,0)==1);assert(get(&b)==1020);
 dispatch_flush(&b,-1);assert(packet(&b,50000,0)==1);assert(get(&b)==50000);raop_buffer_flush(&b,-1);
 // Rate-independent RTP sequence handling across repeated switches/intermittence.
 init(&b,65000);unsigned seq=65001;unsigned delivered=0;
 for(unsigned clip=0;clip<1000;clip++){
  if(clip%3==0){dispatch_flush(&b,(unsigned short)seq);dispatch_flush(&b,(unsigned short)seq);}
  for(int n=0;n<3;n++){assert(packet(&b,(unsigned short)seq,(n%2)+1)==1);seq=(seq+1)&65535;}
  assert(get(&b)==-1); /* Known no-data period must not decode fake PCM. */
  assert(packet(&b,(unsigned short)seq,0)==1);assert(get(&b)==(int)(unsigned short)seq);assert(payload_len==1&&payload_first==42);delivered++;seq=(seq+1)&65535;
 }
 assert(delivered==1000);raop_buffer_flush(&b,-1);
 init(&b,1000);assert(packet(&b,1001,6)==1);assert(get(&b)==1001&&payload_len==32);raop_buffer_flush(&b,-1); /* 44-byte non-ALAC stays real payload. */
 for(unsigned rate=44100;rate<=48000;rate+=3900)printf("Queue behavior independent of%uHz; baseline255intervals of480frames = %.3fs (arithmetic only, no codec rate-switch claim).\n",rate,255.0*480/rate);
 puts("CANDIDATE host-only PASS:12/16-byte markers, repeat/duplicate/late/wrap, genuine loss/resend preserved, malformed/lookalike payloads,256-marker bound,startup markers,FLUSHinclusive/no-seq. Only archive overlay under test; no device/codec playback claim.");
#endif
}
