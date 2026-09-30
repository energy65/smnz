package com.shenma.tvlauncher.vod.domain;

import com.shenma.tvlauncher.utils.Constant;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

public class VideoList implements Serializable{

	public HashMap<String, List> getPlayUrlMap() {
		return mPlayUrlMap;
	}

	public List<String> getPlaySrcList() {
		return mPlaySrcList;
	}

	public List<VodUrl> getPlayUrlList(String srcName){
		if (srcName!=null && mPlayUrlMap!=null && mPlaySrcList!=null && mPlayUrlMap.containsKey(srcName)){
			return mPlayUrlMap.get(srcName);
		}
		return null;
	}

	private HashMap<String,List> mPlayUrlMap;
	private List<String> mPlaySrcList;

	private String mVodPlay;
	private String mVodUrl;

	public VideoList(String vod_play,String vod_url){
		if(mPlayUrlMap==null){
			mPlayUrlMap=new HashMap<>();
		}
		mVodPlay=vod_play;
		mVodUrl=vod_url;

		parseUrl(mVodPlay,mVodUrl);

	}

	private void parseUrl(String vod_play,String vodUrl){
		String[] playNameList=vod_play.split("\\$\\$\\$");
		mPlaySrcList = Arrays.asList(playNameList);

		String[] urlList=vodUrl.split("\\$\\$\\$");

		for (int i = 0; i< mPlaySrcList.size(); i++){
			String name= mPlaySrcList.get(i);

			String urlStr=urlList[i];

			String[] NameUrls=urlStr.split("\\r");


			ArrayList<VodUrl> urlLists=new ArrayList<>();
			for(String nameurl:NameUrls){

				String[] infos= nameurl.split("\\$");

				VodUrl playUrlInfo=new VodUrl();

				if(infos.length>1) {
					playUrlInfo.setTitle(generateUrlName(infos[0]));
					playUrlInfo.setUrl(infos[1]);
				}
				else{
					playUrlInfo.setTitle(name);
					playUrlInfo.setUrl(infos[0]);
				}

				urlLists.add(playUrlInfo);

			}

			mPlayUrlMap.put(name,urlLists);

		}
	}
	private String generateUrlName(String origName){
		if (isNumeric(origName)){
			return "第"+origName+"集";
		}
		else{
			return origName;
		}
	}
	public static boolean isNumeric(String str){
		Pattern pattern = Pattern.compile("[0-9]*");
		return pattern.matcher(str).matches();
	}

	public static String getProxiedUrl(String vodUrl){
		if (vodUrl == null || vodUrl.length() == 0) {
			return vodUrl;
		}
		String urlHost="";
		try {
			URL url=new URL(vodUrl);
			urlHost=url.getHost();
		} catch (MalformedURLException e) {
			return vodUrl;
		}

		String vips[]={"qq.com","youku.com","mgtv.com","sohu.com","letv.com","pptv.com","iqiyi.com","wasu.com","baofeng.com","kankan.com"};
		for (String vip:vips){
			if (urlHost.endsWith(vip)){
				String proxy = Constant.VIP_PROXY_URL;
				if (proxy == null || proxy.length() == 0) {
					return vodUrl;
				}
				return proxy+vodUrl;
			}
		}
		return vodUrl;
	}

	/** 可直接交给原生播放器解码的媒体文件扩展名 */
	private static final String[] MEDIA_EXT = {
			".m3u8", ".mp4", ".flv", ".ts", ".mkv", ".avi", ".mov", ".wmv",
			".m4v", ".mpg", ".mpeg", ".webm", ".3gp", ".rmvb", ".rm", ".asf",
			".mp3", ".aac", ".flac", ".wav", ".m4a", ".ogg", ".wma", ".m3u"
	};

	/**
	 * 播放地址是否指向一个直链媒体文件。
	 * 判定前会剥离查询串与锚点，避免 CDN 用 ?token=xxx 结尾时误判。
	 */
	public static boolean isDirectMedia(String vodUrl){
		if (vodUrl == null) {
			return false;
		}
		String path = vodUrl.trim();
		int cut = path.length();
		for (int i = 0; i < path.length(); i++) {
			char c = path.charAt(i);
			if (c == '?' || c == '#') {
				cut = i;
				break;
			}
		}
		path = path.substring(0, cut).toLowerCase();
		for (String ext : MEDIA_EXT) {
			if (path.endsWith(ext)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 该地址是否必须走 XWalk 网页播放器。
	 * CMS/爬虫返回的播放地址有大量是分享页或播放器页（如 .../share/xxxx.html），
	 * 这类地址不是媒体文件，原生播放器无法解码；判断依据是地址本身形态，
	 * 而不是硬编码站点域名。
	 */
	public static boolean shouldUseWebPlayer(String vodUrl){
		if (vodUrl == null) {
			return false;
		}
		String u = vodUrl.trim();
		if (!u.startsWith("http://") && !u.startsWith("https://")) {
			// 非 http 地址（本地文件、未知协议）交给原生播放器
			return false;
		}
		return !isDirectMedia(u);
	}

	private List<VodUrl> letv_com;
	private List<VodUrl> pps_tv;
	private List<VodUrl> pptv_com;
	private List<VodUrl> sohu_com;
	private List<VodUrl> kankan_com;
	private List<VodUrl> qq_com;
	private List<VodUrl> tudou_com;
	private List<VodUrl> iqiyi_com;
	private List<VodUrl> youku_com;
	private List<VodUrl> baofeng_com;
	private List<VodUrl> m1905_com;
	private List<VodUrl> wasu_cn;
	private List<VodUrl> cntv_cn;
	private List<VodUrl> x61_com;
	private List<VodUrl> funshion_com;
	private List<VodUrl> hunantv_com;
	//电影端
	private List<VodUrl> pps;
	private List<VodUrl> sohu;
	private List<VodUrl> fengxing;
	private List<VodUrl> qq;
	private List<VodUrl> pptv;
	private List<VodUrl> tudou;
	private List<VodUrl> m1905;
	private List<VodUrl> tv189;
	private List<VodUrl> yuku;
	private List<VodUrl> qiyi;
	private List<VodUrl> xunlei;
	private List<VodUrl> bdwp;//百度网盘
	private List<VodUrl> flv;//FLV视频
	private List<VodUrl> wole;//56网
	private List<VodUrl> letv;//乐视网
	public List<VodUrl> getLetv_com() {
		return letv_com;
	}
	public void setLetv_com(List<VodUrl> letv_com) {
		this.letv_com = letv_com;
	}
	public List<VodUrl> getPps_tv() {
		return pps_tv;
	}
	public void setPps_tv(List<VodUrl> pps_tv) {
		this.pps_tv = pps_tv;
	}
	public List<VodUrl> getPptv_com() {
		return pptv_com;
	}
	public void setPptv_com(List<VodUrl> pptv_com) {
		this.pptv_com = pptv_com;
	}
	public List<VodUrl> getSohu_com() {
		return sohu_com;
	}
	public void setSohu_com(List<VodUrl> sohu_com) {
		this.sohu_com = sohu_com;
	}
	public List<VodUrl> getKankan_com() {
		return kankan_com;
	}
	public void setKankan_com(List<VodUrl> kankan_com) {
		this.kankan_com = kankan_com;
	}
	public List<VodUrl> getQq_com() {
		return qq_com;
	}
	public void setQq_com(List<VodUrl> qq_com) {
		this.qq_com = qq_com;
	}
	public List<VodUrl> getTudou_com() {
		return tudou_com;
	}
	public void setTudou_com(List<VodUrl> tudou_com) {
		this.tudou_com = tudou_com;
	}
	public List<VodUrl> getIqiyi_com() {
		return iqiyi_com;
	}
	public void setIqiyi_com(List<VodUrl> iqiyi_com) {
		this.iqiyi_com = iqiyi_com;
	}
	public List<VodUrl> getYouku_com() {
		return youku_com;
	}
	public void setYouku_com(List<VodUrl> youku_com) {
		this.youku_com = youku_com;
	}
	public List<VodUrl> getBaofeng_com() {
		return baofeng_com;
	}
	public void setBaofeng_com(List<VodUrl> baofeng_com) {
		this.baofeng_com = baofeng_com;
	}
	public List<VodUrl> getM1905_com() {
		return m1905_com;
	}
	public void setM1905_com(List<VodUrl> m1905_com) {
		this.m1905_com = m1905_com;
	}
	public List<VodUrl> getWasu_cn() {
		return wasu_cn;
	}
	public void setWasu_cn(List<VodUrl> wasu_cn) {
		this.wasu_cn = wasu_cn;
	}
	public List<VodUrl> getCntv_cn() {
		return cntv_cn;
	}
	public void setCntv_cn(List<VodUrl> cntv_cn) {
		this.cntv_cn = cntv_cn;
	}
	public List<VodUrl> getX61_com() {
		return x61_com;
	}
	public void setX61_com(List<VodUrl> x61_com) {
		this.x61_com = x61_com;
	}
	public List<VodUrl> getFunshion_com() {
		return funshion_com;
	}
	public void setFunshion_com(List<VodUrl> funshion_com) {
		this.funshion_com = funshion_com;
	}
	public List<VodUrl> getPps() {
		return pps;
	}
	public void setPps(List<VodUrl> pps) {
		this.pps = pps;
	}
	public List<VodUrl> getSohu() {
		return sohu;
	}
	public void setSohu(List<VodUrl> sohu) {
		this.sohu = sohu;
	}
	public List<VodUrl> getFengxing() {
		return fengxing;
	}
	public void setFengxing(List<VodUrl> fengxing) {
		this.fengxing = fengxing;
	}
	public List<VodUrl> getQq() {
		return qq;
	}
	public void setQq(List<VodUrl> qq) {
		this.qq = qq;
	}
	public List<VodUrl> getPptv() {
		return pptv;
	}
	public void setPptv(List<VodUrl> pptv) {
		this.pptv = pptv;
	}
	public List<VodUrl> getTudou() {
		return tudou;
	}
	public void setTudou(List<VodUrl> tudou) {
		this.tudou = tudou;
	}
	public List<VodUrl> getM1905() {
		return m1905;
	}
	public void setM1905(List<VodUrl> m1905) {
		this.m1905 = m1905;
	}
	public List<VodUrl> getTv189() {
		return tv189;
	}
	public void setTv189(List<VodUrl> tv189) {
		this.tv189 = tv189;
	}
	public List<VodUrl> getYuku() {
		return yuku;
	}
	public void setYuku(List<VodUrl> yuku) {
		this.yuku = yuku;
	}
	public List<VodUrl> getQiyi() {
		return qiyi;
	}
	public void setQiyi(List<VodUrl> qiyi) {
		this.qiyi = qiyi;
	}
	public List<VodUrl> getXunlei() {
		return xunlei;
	}
	public void setXunlei(List<VodUrl> xunlei) {
		this.xunlei = xunlei;
	}
	public List<VodUrl> getBdwp() {
		return bdwp;
	}
	public void setBdwp(List<VodUrl> bdwp) {
		this.bdwp = bdwp;
	}
	public List<VodUrl> getFlv() {
		return flv;
	}
	public void setFlv(List<VodUrl> flv) {
		this.flv = flv;
	}
	public List<VodUrl> getWole() {
		return wole;
	}
	public void setWole(List<VodUrl> wole) {
		this.wole = wole;
	}
	public List<VodUrl> getLetv() {
		return letv;
	}
	public void setLetv(List<VodUrl> letv) {
		this.letv = letv;
	}
	public List<VodUrl> getHunantv_com() {
		return hunantv_com;
	}
	public void setHunantv_com(List<VodUrl> hunantv_com) {
		this.hunantv_com = hunantv_com;
	}
	@Override
	public String toString() {
		return "VideoList [letv_com=" + letv_com + ", pps_tv=" + pps_tv
				+ ", pptv_com=" + pptv_com + ", sohu_com=" + sohu_com
				+ ", kankan_com=" + kankan_com + ", qq_com=" + qq_com
				+ ", tudou_com=" + tudou_com + ", iqiyi_com=" + iqiyi_com
				+ ", youku_com=" + youku_com + ", baofeng_com=" + baofeng_com
				+ ", m1905_com=" + m1905_com + ", wasu_cn=" + wasu_cn
				+ ", cntv_cn=" + cntv_cn + ", x61_com=" + x61_com
				+ ", funshion_com=" + funshion_com + ", hunantv_com="
				+ hunantv_com + ", pps=" + pps + ", sohu=" + sohu
				+ ", fengxing=" + fengxing + ", qq=" + qq + ", pptv=" + pptv
				+ ", tudou=" + tudou + ", m1905=" + m1905 + ", tv189=" + tv189
				+ ", yuku=" + yuku + ", qiyi=" + qiyi + ", xunlei=" + xunlei
				+ ", bdwp=" + bdwp + ", flv=" + flv + ", wole=" + wole
				+ ", letv=" + letv + "]";
	}
	
}
