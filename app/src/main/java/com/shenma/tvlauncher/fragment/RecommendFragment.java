package com.shenma.tvlauncher.fragment;
import java.lang.reflect.Field;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageLoader;
import com.shenma.tvlauncher.R;
import com.shenma.tvlauncher.UserActivity;
import com.shenma.tvlauncher.application.MyVolley;
import com.shenma.tvlauncher.netsource.TvBoxConfig;
import com.shenma.tvlauncher.utils.Logger;
import com.shenma.tvlauncher.utils.ScaleAnimEffect;
import com.shenma.tvlauncher.utils.Utils;
import com.shenma.tvlauncher.vod.SearchActivity;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
/**
 * @Description 推荐
 * @author joychang
 *
 */
public class RecommendFragment extends BaseFragment implements OnFocusChangeListener,OnClickListener{
	
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Logger.d(TAG, "onCreate()........");
	}
	
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		Logger.d(TAG, "onCreateView()........");
		if(container==null){
			return null;
		}
		if(null == view){
			view = inflater.inflate(R.layout.layout_recommend, container,false);
			init();
		}else{
			((ViewGroup)view.getParent()).removeView(view);
		}
		if(netData == null){
			initData();
		}
		return view;
	}
	
	@Override
	public void onStop() {
		super.onStop();
		Logger.d(TAG, "onStop()........");
		if(null!=mQueue){
			mQueue.stop();
		}
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		Logger.d(TAG, "onDestroy()........");
		if(null!=mQueue){
			mQueue.cancelAll(this);
		}
	}
	
	@Override
	public void onResume() {
		super.onResume();
		Logger.d(TAG, "onResume()........");
	}
	@Override
	public void onDetach() {
		super.onDetach();
		try {
			Field childFragmentManager = Fragment.class
					.getDeclaredField("mChildFragmentManager");
			childFragmentManager.setAccessible(true);
			childFragmentManager.set(this, null);

		} catch (NoSuchFieldException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
	
	//初始化
	private void init(){
		loadViewLayout();
		findViewById();
		setListener();
		//re_fls[0].requestFocus();
	}
	
	//从 TVBox 网络点播源（forever.json 第一个站点）加载最新影视
	private void initData(){
			imageLoader = MyVolley.getImageLoader();
			new Thread(new Runnable() {
				@Override
				public void run() {
					final ArrayList<RecItem> items = new ArrayList<RecItem>();
					try {
						ArrayList<TvBoxConfig.Site> sites = TvBoxConfig.getSites(context);
						if (sites != null && !sites.isEmpty()) {
							String api = sites.get(0).api;
							JSONObject j = new JSONObject(TvBoxConfig.fetchText(api + "?ac=videolist&pg=1", 15000));
							JSONArray list = j.optJSONArray("list");
							if (list != null) {
								for (int i = 0; i < list.length() && items.size() < 6; i++) {
									JSONObject v = list.optJSONObject(i);
									if (v == null) {
										continue;
									}
									RecItem r = new RecItem();
									r.id = v.optString("vod_id");
									r.title = v.optString("vod_name");
									r.pic = v.optString("vod_pic");
									if (r.title.length() > 0) {
										items.add(r);
									}
								}
							}
						}
					} catch (Exception e) {
					}
					if (home != null) {
						home.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								showNetRecommend(items);
							}
						});
					}
				}
			}, "net-recommend").start();
	}

	//填充最新影视推荐位（iv_re_3~8 共 6 个格子）
	private void showNetRecommend(ArrayList<RecItem> items){
		if (items == null || items.isEmpty()) {
			return;
		}
		netData = items;
		for (int i = 0; i < items.size() && i < 6; i++) {
			int slot = i + 3;
			tvs[i].setText(items.get(i).title);
			tvs[i].setVisibility(View.VISIBLE);
			if (items.get(i).pic != null && items.get(i).pic.length() > 0) {
				imageLoader.get(items.get(i).pic,
						ImageLoader.getImageListener(re_typeLogs[slot], re_typebgs[slot], re_typebgs[slot]));
			}
		}
	}

	//点击推荐位：进入网络点播并自动打开该影片详情
	private void openNetVod(int idx){
		if (netData != null && idx < netData.size()) {
			Intent i = new Intent();
			i.setClass(home, com.shenma.tvlauncher.netsource.NetVodActivity.class);
			i.putExtra("openVodId", netData.get(idx).id);
			startActivity(i);
		}
	}

	private static class RecItem {
		String id;
		String title;
		String pic;
	}
	
	
	protected void loadViewLayout() {
		re_fls = new FrameLayout[9];
		re_typeLogs = new ImageView[9];
		re_typebgs = new int[9];
		rebgs = new ImageView[9];
		tvs=  new TextView[6];
		animEffect = new ScaleAnimEffect();
	}


	protected void findViewById() {
		re_fls[0] = (FrameLayout) view.findViewById(R.id.fl_re_0);
		re_fls[1] = (FrameLayout) view.findViewById(R.id.fl_re_1);
		re_fls[2] = (FrameLayout) view.findViewById(R.id.fl_re_2);
		re_fls[3] = (FrameLayout) view.findViewById(R.id.fl_re_3);
		re_fls[4] = (FrameLayout) view.findViewById(R.id.fl_re_4);
		re_fls[5] = (FrameLayout) view.findViewById(R.id.fl_re_5);
		re_fls[6] = (FrameLayout) view.findViewById(R.id.fl_re_6);
		re_fls[7] = (FrameLayout) view.findViewById(R.id.fl_re_7);
		re_fls[8] = (FrameLayout) view.findViewById(R.id.fl_re_8);
		
		re_typeLogs[0] = (ImageView) view.findViewById(R.id.iv_re_0);
		re_typeLogs[1] = (ImageView) view.findViewById(R.id.iv_re_1);
		re_typeLogs[2] = (ImageView) view.findViewById(R.id.iv_re_2);
		re_typeLogs[3] = (ImageView) view.findViewById(R.id.iv_re_3);
		re_typeLogs[4] = (ImageView) view.findViewById(R.id.iv_re_4);
		re_typeLogs[5] = (ImageView) view.findViewById(R.id.iv_re_5);
		re_typeLogs[6] = (ImageView) view.findViewById(R.id.iv_re_6);
		re_typeLogs[7] = (ImageView) view.findViewById(R.id.iv_re_7);
		re_typeLogs[8] = (ImageView) view.findViewById(R.id.iv_re_8);
		
		re_typebgs[0] =  R.drawable.fl_re_1;
		re_typebgs[1] =  R.drawable.fl_re_1;
		re_typebgs[2] =  R.drawable.fl_re_1;
		re_typebgs[3] =  R.drawable.fl_re_0;
		re_typebgs[4] =  R.drawable.fl_re_1;
		re_typebgs[5] =  R.drawable.fl_re_1;
		re_typebgs[6] =  R.drawable.fl_re_3;
		re_typebgs[7] =  R.drawable.fl_re_4;
		re_typebgs[8] =  R.drawable.fl_re_4;
		
		
		rebgs[0] = (ImageView) view.findViewById(R.id.re_bg_0);
		rebgs[1] = (ImageView) view.findViewById(R.id.re_bg_1);
		rebgs[2] = (ImageView) view.findViewById(R.id.re_bg_2);
		rebgs[3] = (ImageView) view.findViewById(R.id.re_bg_3);
		rebgs[4] = (ImageView) view.findViewById(R.id.re_bg_4);
		rebgs[5] = (ImageView) view.findViewById(R.id.re_bg_5);
		rebgs[6] = (ImageView) view.findViewById(R.id.re_bg_6);
		rebgs[7] = (ImageView) view.findViewById(R.id.re_bg_7);
		rebgs[8] = (ImageView) view.findViewById(R.id.re_bg_8);
		
		tvs[0] = (TextView) view.findViewById(R.id.tv_re_3);
		tvs[1] = (TextView) view.findViewById(R.id.tv_re_4);
		tvs[2] = (TextView) view.findViewById(R.id.tv_re_5);
		tvs[3] = (TextView) view.findViewById(R.id.tv_re_6);
		tvs[4] = (TextView) view.findViewById(R.id.tv_re_7);
		tvs[5] = (TextView) view.findViewById(R.id.tv_re_8);
	}

	private int getPX(int i){
		return getResources().getDimensionPixelSize(i);
	}
	
	protected void setListener() {
		for(int i=0;i<re_typeLogs.length;i++){
			re_typeLogs[i].setOnClickListener(this);
			//if(ISTV){
//				re_typeLogs[i].setOnFocusChangeListener(this);
			//}
			re_typeLogs[i].setOnFocusChangeListener(this);
			rebgs[i].setVisibility(View.GONE);
		}
	}
	
	
	
	

	@Override
	public void onFocusChange(View v, boolean hasFocus) {
//		int[] location = new int[2];
//		re_typeLogs[0].getLocationOnScreen(location);
//		int width = re_typeLogs[0].getWidth();
//		int height = re_typeLogs[0].getHeight();
//		float x = (float) location[0];
//		float y = (float) location[1];
//		Logger.d(TAG, "X="+x+"---Y="+y);
		//home.flyWhiteBorder(width, height, x, y);
		int paramInt = 0;
		switch (v.getId()) {
		case R.id.iv_re_0:
			paramInt = 0;
			break;
		case R.id.iv_re_1:
			paramInt = 1;
			break;
		case R.id.iv_re_2:
			paramInt = 2;
			break;
		case R.id.iv_re_3:
			paramInt = 3;
			break;
		case R.id.iv_re_4:
			paramInt = 4;
			break;
		case R.id.iv_re_5:
			paramInt = 5;
			break;
		case R.id.iv_re_6:
			paramInt = 6;
			break;
		case R.id.iv_re_7:
			paramInt = 7;
			break;
		case R.id.iv_re_8:
			paramInt = 8;
			break;
		}
		if(hasFocus){
			showOnFocusTranslAnimation(paramInt);
			if(null!=home.whiteBorder){
				home.whiteBorder.setVisibility(View.VISIBLE);
			}
			flyAnimation(paramInt);
		}else{
			showLooseFocusTranslAinimation(paramInt);
		}
		for (TextView tv : tvs) {
			if(tv.getVisibility()!=View.GONE) {
				tv.setVisibility(View.GONE);
			}
		}
		
	}
	/**
	 * 飞框焦点动画
	 * @param paramInt
	 */
	private void flyAnimation(int paramInt){
		int[] location = new int[2];
		re_typeLogs[paramInt].getLocationOnScreen(location);
		int width = re_typeLogs[paramInt].getWidth();
		int height = re_typeLogs[paramInt].getHeight();
		float x = (float) location[0];
		float y = (float) location[1];
		Logger.v("joychang", "paramInt="+paramInt+"..x="+x+"...y="+y);
		switch (paramInt) {
		case 0:
//			width = width+1;
//			height = height+3;
//			x = (float) location[0]-21;
//			y = (float) location[1]-7;
			//x = 42-21;
			//y = 189-7;
			if(mHeight>1000&&mWidth>1000){
				//1080p
				x = getResources().getDimensionPixelSize(R.dimen.sm_49); 
				y = getResources().getDimensionPixelSize(R.dimen.sm_190)-3;  
			}else {
				x = getResources().getDimensionPixelSize(R.dimen.sm_21);
				y = getResources().getDimensionPixelSize(R.dimen.sm_164);
			}
			break;
		case 1:
//			width = width+1;
//			height = height+3;
//			x = (float) location[0]-21;
//			y = (float) location[1];
//			x = 42-21;
			if(mHeight>1000&&mWidth>1000){
				//1080p
				y = getResources().getDimensionPixelSize(R.dimen.sm_310)+14;
				x = getResources().getDimensionPixelSize(R.dimen.sm_49);
			}else {
				y = 298;
				x = getResources().getDimensionPixelSize(R.dimen.sm_21);
			}
			//y = getResources().getDimensionPixelSize(R.dimen.sm_316);
			break;
		case 2:
//			width = width+1;
//			height = height+3;
//			x = (float) location[0]-21;
//			y = (float) location[1]+4;
			if(mHeight>1000&&mWidth>1000){
				//1080p
				x = getResources().getDimensionPixelSize(R.dimen.sm_49);
				y = getResources().getDimensionPixelSize(R.dimen.sm_450)-1;
			}else {
				x = 42-21;
				y = 425+4;
			}
			break;
		case 3:
//			x = (float) location[0]+154;
//			y = (float) location[1]+60;
			if(mHeight>1000&&mWidth>1000){
				//1080p
				width = width+24+14;
				height = height+13+8;
				x = getResources().getDimensionPixelSize(R.dimen.sm_370)-2;
				y = getResources().getDimensionPixelSize(R.dimen.sm_252)+1;
			}else {
				width = width+24;
				height = height+16;
				x = (float) 188+154;
				y = (float) 189+40;
			}
			break;
		case 4:
//			x = (float) location[0]+28;
//			y = (float) location[1]+6;
			if(mHeight>1000&&mWidth>1000){
				//1080p
				width = width+13+6;
				height = height+7+5;
				x = getResources().getDimensionPixelSize(R.dimen.sm_246) - 2;
				y = getResources().getDimensionPixelSize(R.dimen.sm_456) + 12;
			}else {
				width = width+13;
				height = height+8;
				x = (float) 188+28;
				y = (float) 436+8;
			}
			break;
		case 5:
//			x = (float) location[0]+38;
//			y = (float) location[1]+7;
			
			if(mHeight>1000&&mWidth>1000){
				//1080p
				width = width+13+6;
				height = height+7+5;
				x = getResources().getDimensionPixelSize(R.dimen.sm_481) + 2;
				y = getResources().getDimensionPixelSize(R.dimen.sm_456) + 12;
			}else {
				width = width+13;
				height = height+8;
				x = (float) 420+38;
				y = (float) 436+8;
			}
			break;
		case 6:
			if(mHeight>1000&&mWidth>1000){
				//1080p
				width = width+15+8;
				height = height+22+13;
				x = getResources().getDimensionPixelSize(R.dimen.sm_746) + 3;
				y = getResources().getDimensionPixelSize(R.dimen.sm_320) + 9;
			}else {
				width = width+18;
				height = height+26;
				x = (float) 654+75;
				y = (float) 189+115;
			}
			break;
		case 7:
			if(mHeight>1000&&mWidth>1000){
				//1080p
				width = width+17+10;
				height = height+12+5;
				x = getResources().getDimensionPixelSize(R.dimen.sm_1000) + 73;
				y = getResources().getDimensionPixelSize(R.dimen.sm_220) + 1;
			}else {
				width = width+17;
				height = height+14;
				x = (float) 924+111;
				y = (float) 189+8;
			}
			break;
		case 8:
			if(mHeight>1000&&mWidth>1000){
				//1080p
				width = width+17+10;
				height = height+12+5;
				x = getResources().getDimensionPixelSize(R.dimen.sm_1000) + 73;
				y = getResources().getDimensionPixelSize(R.dimen.sm_435) - 2;
			}else {
				width = width+17;
				height = height+14;
				x = (float) 924+111;
				y = (float) 394+18;
			}
			break;

		}
		Logger.d(TAG, "X="+x+"---Y="+y);
		home.flyWhiteBorder(width, height, x, y);
}
	
	private void showOnFocusTranslAnimation(int paramInt){
		
		re_fls[paramInt].bringToFront();//将当前FrameLayout置为顶层
		Animation mtAnimation = null;
		Animation msAnimation = null;
		switch (paramInt) {
		case 0:
			mtAnimation = animEffect.translAnimation(0.0f, -20.0f, 0.0f, -5.0f);
			break;
		case 1:
			mtAnimation = animEffect.translAnimation(0.0f, -20.0f, 0.0f, 1.0f);
			break;
		case 2:
			mtAnimation = animEffect.translAnimation(0.0f, -20.0f, 0.0f, 5.0f);
			break;
		case 3:
			mtAnimation = animEffect.translAnimation(0.0f, -10.0f, 0.0f, -5.0f);
			break;
		case 4:
			mtAnimation = animEffect.translAnimation(0.0f, -20.0f, 0.0f, 5.0f);
			break;
		case 5:
			mtAnimation = animEffect.translAnimation(0.0f, -10.0f, 0.0f, 5.0f);
			break;
		case 6:
			mtAnimation = animEffect.translAnimation(0.0f, 10.0f, 0.0f, 0.0f);
			break;
		case 7:
			mtAnimation = animEffect.translAnimation(0.0f, 20.0f, 0.0f, -5.0f);
			break;
		case 8:
			mtAnimation = animEffect.translAnimation(0.0f, 20.0f, 0.0f, 5.0f);
			break;
		default:
			break;
		}
		msAnimation = animEffect.ScaleAnimation(1.0F, 1.05F, 1.0F, 1.05F);
		AnimationSet set=new AnimationSet(true);
		set.addAnimation(msAnimation);
		set.addAnimation(mtAnimation);
		set.setFillAfter(true);
//		set.setFillEnabled(true);
		set.setAnimationListener(new MyOnFocusAnimListenter(paramInt));
//		ImageView iv = re_typeLogs[paramInt];
//		iv.setAnimation(set);
//		set.startNow(); TODO
		re_fls[paramInt].startAnimation(set);
		//re_fls[paramInt].startAnimation(set);

	}
	
	/**
	 * 失去焦点缩小
	 * @param paramInt
	 */
	private void showLooseFocusTranslAinimation(int paramInt) {
		Animation mAnimation = null;
		Animation mtAnimation = null;
		Animation msAnimation = null;
		AnimationSet set = null;
		switch (paramInt) {
		case 0:
			mtAnimation = animEffect.translAnimation(-20.0f, 0.0f, -5.0f, 0.0f);
			break;
		case 1:
			mtAnimation = animEffect.translAnimation(-20.0f, 0.0f, 1.0f, 0.0f);
			break;
		case 2:
			mtAnimation = animEffect.translAnimation(-20.0f, 0.0f, 5.0f, 0.0f);
			break;
		case 3:
			mtAnimation = animEffect.translAnimation(-10.0f, 0.0f, -5.0f, 0.0f);
			break;
		case 4:
			mtAnimation = animEffect.translAnimation(-20.0f, 0.0f, 5.0f, 0.0f);
			break;
		case 5:
			mtAnimation = animEffect.translAnimation(-10.0f, 0.0f, 5.0f, 0.0f);
			break;
		case 6:
			mtAnimation = animEffect.translAnimation(10.0f, 0.0f, 0.0f, 0.0f);
			break;
		case 7:
			mtAnimation = animEffect.translAnimation(20.0f, 0.0f, -5.0f, 0.0f);
			break;
		case 8:
			mtAnimation = animEffect.translAnimation(20.0f, 0.0f, 5.0f, 0.0f);
			break;

		default:
			break;
			
		}
		msAnimation = animEffect.ScaleAnimation(1.05F, 1.0F, 1.05F, 1.0F);
		set =new AnimationSet(true);
		set.addAnimation(msAnimation);
		set.addAnimation(mtAnimation);
		set.setFillAfter(true);
//		set.setFillEnabled(true);
		set.setAnimationListener(new MyLooseFocusAnimListenter(paramInt));
//		ImageView iv = re_typeLogs[paramInt];
//		iv.setAnimation(set);
//		set.startNow();
//		mAnimation.setAnimationListener(new MyLooseFocusAnimListenter(paramInt));
		rebgs[paramInt].setVisibility(View.GONE);
		re_fls[paramInt].startAnimation(set);
	}
	
	/**
	 * 获取焦点时动画监听
	 * @author joychang
	 *
	 */
	public class MyOnFocusAnimListenter implements Animation.AnimationListener {

		private int paramInt;

		public MyOnFocusAnimListenter(int paramInt) {
			this.paramInt = paramInt;
		}

		@Override
		public void onAnimationStart(Animation animation) {
			
		}

		@Override
		public void onAnimationEnd(Animation animation) {
			Logger.v("joychang", "onAnimationEnd");
			rebgs[paramInt].setVisibility(View.VISIBLE);
//			Animation localAnimation =animEffect
//					.alphaAnimation(0.0F, 1.0F, 150L, 0L);
//			localImageView.startAnimation(localAnimation);
			if(paramInt >= 3) {
				tvs[paramInt-3].setVisibility(View.VISIBLE);
			}
		}
		@Override
		public void onAnimationRepeat(Animation animation) {

		}

	}
	
	
	/**
	 * 获取焦点时动画监听
	 * @author joychang
	 *
	 */
	public class MyLooseFocusAnimListenter implements Animation.AnimationListener {
		
		private int paramInt;
		
		public MyLooseFocusAnimListenter(int paramInt) {
			this.paramInt = paramInt;
		}
		
		@Override
		public void onAnimationStart(Animation animation) {
			
		}
		
		@Override
		public void onAnimationEnd(Animation animation) {
			Logger.v("joychang", "onAnimationEnd");
//			Animation localAnimation =animEffect
//					.alphaAnimation(0.0F, 1.0F, 150L, 0L);
//			localImageView.startAnimation(localAnimation);
		}
		
		@Override
		public void onAnimationRepeat(Animation animation) {
			
		}
		
	}
	
	/**
	 * 根据状态来下载或者打开app
	 * @author drowtram
	 * @param apkurl
	 * @param packName
	 */
	private void startOpenOrDownload(String apkurl, String packName, String fileName) {
		//判断当前应用是否已经安装
		for (PackageInfo pack : home.packLst) {
			if(pack.packageName.equals(packName)){
				//已安装了apk，则直接打开
				Intent intent = getActivity().getPackageManager().getLaunchIntentForPackage(packName);
				startActivity(intent);  
				return;
			}
		}
		//如果没有安装，则查询本地是否有安装包文件，有则直接安装
		if(!Utils.startCheckLoaclApk(home,fileName)){
			//如果没有安装包  则进行下载安装
			Utils.startDownloadApk(home,apkurl,null);
		}
	}
	
	@Override
	public void onClick(View v) {
		Intent i;
		switch (v.getId()) {
		case R.id.iv_re_0:
			//搜索
			i = new Intent();
			i.setClass(home, SearchActivity.class);
			i.putExtra("TYPE", "ALL");
			startActivity(i);
			break;
		case R.id.iv_re_1:
			//user
			i = new Intent();
			i.setClass(home, UserActivity.class);
			startActivity(i);
			//Utils.showToast(home, "即将开放,敬请期待！", R.drawable.toast_smile);
			break;
		case R.id.iv_re_2:
			//应用
			//if(ISTV){
			String apkUrl = Utils.getFormInfo(getClass(), 1);
			String apkPack = Utils.getFormInfo(getClass(), 2);
			Logger.d("zhouchuan","apkUrl="+apkUrl+"apkPack="+apkPack);
			startOpenOrDownload(apkUrl, apkPack, apkUrl.substring(apkUrl.lastIndexOf("/")+1));
//			}else{
//				Utils.showToast(home, "检测到您是手机设备，不适合使用TV商城哦", R.drawable.toast_smile);
//			}
			//Utils.showToast(home, "小米商城暂未开放！", R.drawable.toast_smile);
			break;
		case R.id.iv_re_3:
			openNetVod(0);
			break;
		case R.id.iv_re_4:
			openNetVod(1);
			break;
		case R.id.iv_re_5:
			openNetVod(2);
			break;
		case R.id.iv_re_6:
			openNetVod(3);
			break;
		case R.id.iv_re_7:
			openNetVod(4);
			break;
		case R.id.iv_re_8:
			openNetVod(5);
			break;
		}
		home.overridePendingTransition(android.R.anim.fade_in,
				android.R.anim.fade_out);
	}
	
	private View view;
	private FrameLayout[] re_fls;
	public ImageView[] re_typeLogs;
	private TextView[] tvs;
	private int[] re_typebgs;
	private ImageView[] rebgs;
	ScaleAnimEffect animEffect;
	private final String TAG = "RecommendFragment"; 
	
	public RequestQueue mQueue;
	public ImageLoader imageLoader;
	private ArrayList<RecItem> netData = null;
	private TextView tv_intro = null;

}
