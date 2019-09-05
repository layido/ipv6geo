package com.hy.open.ipv6;

/**
 *
 */

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.Arrays;

/**
 *  2019-9-4 created
 * ip数据库文件使用纯真网络ipv6包，当前包版本20190812，可从http://ip.zxinc.org网站下载更新数据
 * 替换main/resource/ipv6wry.db
 *
 * 查询ipv6地址geo位置信息
 * 起始网段，结束网段，省市区，公司
 */

public class IpdbV6 {
    private byte[] img;
    private BigInteger firstIndex;
    private BigInteger indexCount;
    private BigInteger offlen;

    private String NO_IPV4_DB = "缺少IPv4数据库";

    private static  IpdbV6 singleton;
    private IpdbV6(){

    }

    /**
     *懒汉单例模式，使用到时才会加载
     * @return
     */
    public synchronized static IpdbV6 getInstance(){
        if(singleton==null){
            singleton = new IpdbV6();
            singleton.init();
        }
        return singleton;
    }


    private String inet_ntoa(BigInteger number){
        String[] addresslist= new String[4];
        addresslist[0]=number.shiftRight(24).and(new BigInteger("ff",16)).toString();
        addresslist[1]=number.shiftRight(16).and(new BigInteger("ff",16)).toString();
        addresslist[2]=number.shiftRight(8).and(new BigInteger("ff",16)).toString();
        addresslist[3]=number.and(new BigInteger("ff",16)).toString();
        return String.join(".",addresslist);
    }

    private String inet_ntoa6(BigInteger number){
        String[] addresslist= new String[4];
        addresslist[0]=number.shiftRight(48).and(new BigInteger("ffff",16)).toString(16);
        addresslist[1]=number.shiftRight(32).and(new BigInteger("ffff",16)).toString(16);
        addresslist[2]=number.shiftRight(16).and(new BigInteger("ffff",16)).toString(16);
        addresslist[3]=number.and(new BigInteger("ffff",16)).toString(16);
        return String.join(":",addresslist)+"::";
    }

    /**
     * 初始化，加载ipdb数据文件到内存
     */
    public void init(){
        InputStream inputStream=null;
        try {
            inputStream = this.getClass().getClassLoader().getResourceAsStream("ipv6wry.db");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = inputStream.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            img = out.toByteArray();
            if(!"IPDB".equals(subString(0,4))){
                System.out.println("error data");
                return;
            }
            if(getLong8(4,2).intValue()>1){
                System.out.println("error fmt");
                return;
            }
            firstIndex =  getLong8(16,8);
            indexCount =getLong8(8,8);
            offlen = getLong8(6, 1);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(inputStream!=null){
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private BigInteger getLong8(int offset,int count){
        byte[] bs = new byte[count+8];
        System.arraycopy(img, offset, bs, 0, count);
        byte[] bs8=new byte[8];
        System.arraycopy(bs, 0, bs8, 0, 8);
        byte[] rbs8=new byte[8];
        for(int i =0;i<8;i++){
            rbs8[i]=bs8[7-i];
        }
        //小端模式读数
        return new BigInteger(rbs8);
    }


    private String getString(int offset){
        int pos = findByte((byte)0,offset);
        String gbStr = subString(offset,pos-offset);
        return gbStr;
    }

    private String getAreaAddr(int offset){
        byte b = img[offset];
        if(b==1 || b==2){
            int p = getLong8(offset + 1, offlen.intValue()).intValue();
            return getAreaAddr(p);
        }else{
            return getString(offset);
        }
    }

    private String[] getAddr(int offset,int ip){
        byte[] tmpImg = img;
        int o = offset;
        byte b = img[offset];
        if(b==1){
            return getAddr(getLong8(o+1,offlen.intValue()).intValue(),0);
        }else{
            String cArea = getAreaAddr(o);
            if(b==2){
                o+=1+offlen.intValue();
            }else{
                o= findByte((byte)0,o)+1;
            }
            String aArea = getAreaAddr(o);
            return new String[]{cArea, aArea};
        }
    }

    private int find(long ip,int l,int r){
        if(r-l<=1){
            return l;
        }
        int m = (l + r) / 2;
        int o = firstIndex.intValue() + m * (8 + offlen.intValue());
        long newIp = getLong8(o,8).longValue();
        if(ip < newIp){
            return find(ip,l,m);
        }else{
            return find(ip,m,r);
        }
    }

    /**
     * 查询ipv6地理位置
     *
     * @param ip ipv6地址
     * @return 起始网段，结束网段，省市区，公司
     */
    public String[] getIPAddr(String ip){
        String i1="";
        String i2="";
        String[] ca = new String[2];
        String[] ccaa=new String[2];
        try{
            BigInteger ip6 = getIpBigInteger(ip);
            BigInteger ipH = ip6.shiftRight(64).and(new BigInteger("FFFFFFFFFFFFFFFF",16));
            int i = find(ipH.longValue(),0,indexCount.intValue());
            int o = firstIndex.intValue() + i*(8+offlen.intValue());
            BigInteger o2 = getLong8(o+8,offlen.intValue());
            ca = getAddr(o2.intValue(),0);
            ccaa = ca;
            i1 = inet_ntoa6(getLong8(o,8));
            try{
                i2 = inet_ntoa6(getLong8(o + 8 + offlen.intValue(),8).subtract(new BigInteger("1")));
            }catch (Exception e){
                i2 = "FFFF:FFFF:FFFF:FFFF::";
            }
            //特殊ip的处理
            if(ip6.equals(new BigInteger("1",16))){
                i1 = "0:0:0:0:0:0:0:1";
                i2 = "0:0:0:0:0:0:0:1";
                ca[0] = ccaa[0] = "本机地址";
            }else if(ipH.equals(new BigInteger("0",16))&&
                    ip6.shiftRight(32).and(new BigInteger("FFFFFFFF",16)).equals(new BigInteger("FFFF",16))){//IPv4映射地址
                BigInteger realip = ip6.and(new BigInteger("FFFFFFFF",16));
                String realipstr = inet_ntoa(realip);
                String realiploc;
                try{
                    String[] result = getIPAddr(realipstr);
                    realiploc = result[2];
                    ccaa[0]=result[3];
                    ccaa[1]=result[4];
                }catch (Exception e){
                    realiploc = NO_IPV4_DB;
                    i1 = "0:0:0:0:0:FFFF:0:0";
                    i2 = "0:0:0:0:0:FFFF:FFFF:FFFF";
                    ca[0] = "IPv4映射地址";
                    ca[1] = ca[1] + "<br/>对应的IPv4地址为" + realipstr + "，位置为" + realiploc;
                }
            }else if(ipH.shiftRight(48).and(new BigInteger("FFFF",16)).equals(new BigInteger("2002",16))){//6to4
                BigInteger realip = ipH.and(new BigInteger("0000FFFFFFFF0000",16)).shiftRight(16);
                String realipstr = inet_ntoa(realip);
                String realiploc;
                try{
                    String[] result = getIPAddr(realipstr);
                    realiploc = result[2];
                    ccaa[0]=result[3];
                    ccaa[1]=result[4];
                }catch (Exception e){
                    realiploc = NO_IPV4_DB;
                }
                ca[1] = ca[1] + "<br/>对应的IPv4地址为" + realipstr + "，位置为" + realiploc;
            }else if(ipH.shiftRight(32).and(new BigInteger("FFFFFFFF",16)).equals(new BigInteger("20010000",16))){// teredo
                BigInteger serverip = ipH.and(new BigInteger("FFFFFFFF",16));
                String serveripstr = inet_ntoa(serverip);
                BigInteger realip = ip6.not().and(new BigInteger("FFFFFFFF",16));
                String realipstr = inet_ntoa(realip);
                String serveriploc;
                String realiploc;
                try{
                    String[] result1 = getIPAddr(serveripstr);
                    serveriploc = result1[2];
                    ccaa[0]=result1[3];
                    ccaa[1]=result1[4];
                    String[] result2 = getIPAddr(realipstr);
                    realiploc = result2[2];
                }catch (Exception e){
                    serveriploc = NO_IPV4_DB;
                    realiploc = NO_IPV4_DB;
                }
                ca[1] = ca[1] + "<br/>Teredo服务器的IPv4地址为" + serveripstr + "，位置为" + serveriploc;
                ca[1] = ca[1] + "<br/>客户端真实的IPv4地址为" + realipstr + "，位置为" + realiploc;
            }else if(ipH.shiftRight(32).and(new BigInteger("FFFFFFFF",16)).equals(new BigInteger("5EFE",16))){//isatap
                BigInteger realip = ip6.and(new BigInteger("FFFFFFFF",16));
                String realipstr = inet_ntoa(realip);
                String realiploc;
                try{
                    String[] result = getIPAddr(realipstr);
                    realiploc= result[2];
                }catch (Exception e){
                    realiploc = NO_IPV4_DB;
                }
                ca[1] = ca[1] + "<br/>ISATAP地址，对应的IPv4地址为" + realipstr + "，位置为" + realiploc;
            }
        }catch (Exception e){
            System.out.println(e.getMessage());
            i1 = "";
            i2 = "";
            ca[0] = ccaa[0] = "错误的IP地址";
            ca[1] = ccaa[1] = "";
        }
        //全局返回
        return new String[]{i1, i2, ca[1] + " " + ca[0], ccaa[1], ccaa[0]};
    }


    private BigInteger getIpBigInteger(String ip){
        String[] splitIp = ip.split(":");
        int neet2Extend = 9-splitIp.length;
        String[] fullIp = new String[8];
        for(int i=0;i<8;i++){
            fullIp[i]="0";
        }
        int indexFullIp=0;
        for(String s : splitIp){
            if("".equals(s)){
                indexFullIp+=neet2Extend;
            }else{
                fullIp[indexFullIp]=s;
                indexFullIp++;
            }
        }
        BigInteger div7 = new BigInteger(fullIp[0],16).shiftLeft(112);
        BigInteger div6 = new BigInteger(fullIp[1],16).shiftLeft(96);
        BigInteger div5 = new BigInteger(fullIp[2],16).shiftLeft(80);
        BigInteger div4 = new BigInteger(fullIp[3],16).shiftLeft(64);
        BigInteger div3 = new BigInteger(fullIp[4],16).shiftLeft(48);
        BigInteger div2 = new BigInteger(fullIp[5],16).shiftLeft(32);
        BigInteger div1 = new BigInteger(fullIp[6],16).shiftLeft(16);
        BigInteger div0 = new BigInteger(fullIp[7],16);
        return div7.add(div6).add(div5).add(div4).add(div3).add(div2).add(div1).add(div0);
    }

    private int findByte(byte b, int offset){
        for(int i=offset;i<img.length;i++){
            if(img[i]==b){
                return i;
            }
        }
        return -1;
    }

    private String subString(int offset,int count){
        byte[] bs = new byte[count];
        System.arraycopy(img, offset, bs, 0, count);
        return new String(bs);
    }

    /**
     *
     */
    public static void main(String[] args){
        IpdbV6 ipdbV6 = IpdbV6.getInstance();
        String[] rs = ipdbV6.getIPAddr("2400:da00::dbf:0:100");
        Arrays.stream(rs).forEach(v->System.out.println(v));
    }
}
