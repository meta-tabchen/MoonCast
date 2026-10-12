package com.mooncast.host;

public final class ProtocolTabLayoutTest {
    private static int checks;
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    public static void main(String[] args) {
        if(args.length>0){
            int[] widths=java.util.Arrays.stream(args[1].split(",")).mapToInt(Integer::parseInt).toArray();
            var p=ProtocolTabLayout.plan(Integer.parseInt(args[0]),widths,20,6,4,4,2);
            System.out.println("{\"stacked\":"+p.stacked()+",\"contentWidth\":"+p.contentWidth()+"}");return;
        }
        for(int viewport:new int[]{240,280,288,320,360,600})for(int scale:new int[]{100,130,200}){
            int[] labels={51*scale/100,83*scale/100,49*scale/100};
            var p=ProtocolTabLayout.plan(viewport,labels,20,6,4,4,2);
            int cell=(p.contentWidth()-12)/3;
            for(int width:labels)require(cell>=width+8+(p.stacked()?0:26),"Full measured label fits its cell");
            require(p.contentWidth()>=viewport,"Content covers viewport or scrolls");
            if(p.contentWidth()>viewport)require(p.stacked(),"Overflow uses uniform stacked layout");
        }
        require(!ProtocolTabLayout.plan(600,new int[]{51,83,49},20,6,4,4,2).stacked(),"Wide layout stays horizontal");
        var narrow=ProtocolTabLayout.plan(288,new int[]{51,83,49},20,6,4,4,2);
        require(narrow.stacked()&&narrow.contentWidth()==288,"Default narrow layout fits every complete label with uniformly stacked icons");
        require(ProtocolTabLayout.plan(288,new int[]{102,166,98},20,6,4,4,2).contentWidth()>288,"Large type scrolls instead of clipping or shrinking");
        System.out.println("PASS protocol tab measured-width policy: "+checks+" checks");
    }
}
