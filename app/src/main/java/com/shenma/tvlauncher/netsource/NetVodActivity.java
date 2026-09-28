package com.shenma.tvlauncher.netsource;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import com.shenma.tvlauncher.R;
import com.shenma.tvlauncher.vod.WebVideoPlayerActivity;
import com.shenma.tvlauncher.vod.domain.VideoInfo;
import com.shenma.tvlauncher.vod.domain.VideoList;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.util.LruCache;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 网络点播（TVBox forever.json 的 type=1 CMS 站点）
 * 苹果CMS V10 接口：ac=list 取分类 / ac=videolist 取列表与详情 / wd 搜索
 * 播放：直链走 NetVodPlayerActivity，VIP 站点经解析代理走 WebVideoPlayerActivity
 */
public class NetVodActivity extends Activity {

	private static class Line {
		String name;
		ArrayList<String[]> eps = new ArrayList<String[]>();// [0]=集名 [1]=地址
	}

	private LinearLayout mSiteRow, mCatRow, mLineRow, mEpisodes;
	private GridView mGrid;
	private TextView mPageInfo, mLoading, mDetailName, mDetailMeta, mDetailIntro;
	private ImageView mDetailPic;
	private View mDetailPanel;
	private EditText mSearchInput;

	private ArrayList<TvBoxConfig.Site> mSites;
	private int mSiteIdx = 0;
	private int mPage = 1;
	private int mPageCount = 1;
	private String mKeyword = "";
	private JSONArray mList = new JSONArray();
	private GridAdapter mAdapter;

	// 详情状态
	private ArrayList<Line> mLines;
	private int mLineIdx = 0;
	private String mVodName = "", mVodId = "", mVodPic = "", mTypeName = "";

	private int mListSeq = 0, mCatSeq = 0;

	private String mPresetCat = "";// 入口预设分类关键词（如 电影/电视剧），匹配不到则回退"全部"

	private LruCache<String, Bitmap> mPicCache = new LruCache<String, Bitmap>(64);
	private Handler mUi = new Handler();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.net_vod_main);
		String pc = getIntent().getStringExtra("presetCat");
		if (pc != null) {
			mPresetCat = pc;
		}
		findViews();
		mAdapter = new GridAdapter();
		mGrid.setAdapter(mAdapter);
		mGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				openDetail(position);
			}
		});
		setBtn(R.id.net_search_btn, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mKeyword = mSearchInput.getText().toString().trim();
				mPage = 1;
				loadList();
			}
		});
		setBtn(R.id.net_page_prev, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mPage > 1) {
					mPage--;
					loadList();
				}
			}
		});
		setBtn(R.id.net_page_next, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mPage < mPageCount) {
					mPage++;
					loadList();
				}
			}
		});
		setBtn(R.id.net_detail_close, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mDetailPanel.setVisibility(View.GONE);
			}
		});
		// 配置加载在子线程（含网络请求）
		mLoading.setVisibility(View.VISIBLE);
		new Thread(new Runnable() {
			@Override
			public void run() {
				final ArrayList<TvBoxConfig.Site> sites = TvBoxConfig.getSites(NetVodActivity.this);
				mUi.post(new Runnable() {
					@Override
					public void run() {
						mLoading.setVisibility(View.GONE);
						mSites = sites;
						if (sites == null || sites.isEmpty()) {
							Toast.makeText(NetVodActivity.this, "网络点播源加载失败，请检查网络", Toast.LENGTH_LONG).show();
							finish();
							return;
						}
						buildSiteRow();
						selectSite(0);
					}
				});
			}
		}, "tvbox-config-init").start();
	}

	private void findViews() {
		mSiteRow = (LinearLayout) findViewById(R.id.net_site_row);
		mCatRow = (LinearLayout) findViewById(R.id.net_cat_row);
		mLineRow = (LinearLayout) findViewById(R.id.net_detail_lines);
		mEpisodes = (LinearLayout) findViewById(R.id.net_detail_episodes);
		mGrid = (GridView) findViewById(R.id.net_vod_grid);
		mPageInfo = (TextView) findViewById(R.id.net_page_info);
		mLoading = (TextView) findViewById(R.id.net_vod_loading);
		mDetailPanel = findViewById(R.id.net_detail_panel);
		mDetailName = (TextView) findViewById(R.id.net_detail_name);
		mDetailMeta = (TextView) findViewById(R.id.net_detail_meta);
		mDetailIntro = (TextView) findViewById(R.id.net_detail_intro);
		mDetailPic = (ImageView) findViewById(R.id.net_detail_pic);
		mSearchInput = (EditText) findViewById(R.id.net_search_input);
	}

	private void setBtn(int id, View.OnClickListener l) {
		((Button) findViewById(id)).setOnClickListener(l);
	}

	private Button makeChip(String text, boolean active) {
		Button b = new Button(this);
		b.setText(text);
		b.setTextSize(13);
		b.setPadding(20, 6, 20, 6);
		b.setTextColor(active ? 0xFF12151C : 0xFFE6E9EE);
		b.setBackgroundColor(active ? 0xFF4FC3F7 : 0xFF2A313D);
		b.setGravity(Gravity.CENTER);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		lp.rightMargin = 8;
		b.setLayoutParams(lp);
		return b;
	}

	/* ==================== 站点 ==================== */

	private void buildSiteRow() {
		mSiteRow.removeAllViews();
		for (int i = 0; i < mSites.size(); i++) {
			final int idx = i;
			Button b = makeChip(mSites.get(i).name, i == mSiteIdx);
			b.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (idx != mSiteIdx) {
						selectSite(idx);
					}
				}
			});
			mSiteRow.addView(b);
		}
	}

	private void selectSite(int idx) {
		mSiteIdx = idx;
		mPage = 1;
		mKeyword = "";
		mSearchInput.setText("");
		buildSiteRow();
		mCatRow.removeAllViews();
		mList = new JSONArray();
		mAdapter.notifyDataSetChanged();
		mLoading.setVisibility(View.VISIBLE);
		final int seq = ++mCatSeq;
		final String api = mSites.get(idx).api;
		new Thread(new Runnable() {
			@Override
			public void run() {
				String cats = "全部";
				ArrayList<String[]> catList = new ArrayList<String[]>();
				catList.add(new String[] { "", "全部" });
				try {
					JSONObject j = new JSONObject(TvBoxConfig.fetchText(api + "?ac=list", 12000));
					JSONArray cls = j.optJSONArray("class");
					if (cls != null) {
						for (int i = 0; i < cls.length(); i++) {
							JSONObject c = cls.optJSONObject(i);
							if (c == null) {
								continue;
							}
							catList.add(new String[] { c.optString("type_id"), c.optString("type_name") });
						}
					}
				} catch (Exception e) {
				}
				if (seq != mCatSeq) {
					return;
				}
				final ArrayList<String[]> fCats = catList;
				final int fActiveIdx = presetCatIndex(catList);
				mUi.post(new Runnable() {
					@Override
					public void run() {
						if (seq != mCatSeq) {
							return;
						}
						mTypeName = fCats.get(fActiveIdx)[1];
						buildCatRow(fCats, fActiveIdx);
						loadList();
					}
				});
			}
		}, "tvbox-cats").start();
	}

	private void buildCatRow(final ArrayList<String[]> cats, int activeIdx) {
		mCatRow.removeAllViews();
		for (int i = 0; i < cats.size(); i++) {
			final int idx = i;
			Button b = makeChip(cats.get(i)[1], i == activeIdx);
			b.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					mTypeName = cats.get(idx)[1];
					mPage = 1;
					selectCat(cats, idx);
				}
			});
			b.setTag(i);
			mCatRow.addView(b);
		}
		mCatRow.setTag(cats);
	}

	private void selectCat(ArrayList<String[]> cats, int activeIdx) {
		for (int i = 0; i < mCatRow.getChildCount(); i++) {
			View v = mCatRow.getChildAt(i);
			boolean active = (i == activeIdx);
			v.setBackgroundColor(active ? 0xFF4FC3F7 : 0xFF2A313D);
			((TextView) v).setTextColor(active ? 0xFF12151C : 0xFFE6E9EE);
		}
		loadList();
	}

	private String curTypeId() {
		Object tag = mCatRow.getTag();
		int active = -1;
		for (int i = 0; i < mCatRow.getChildCount(); i++) {
			View v = mCatRow.getChildAt(i);
			if (((TextView) v).getCurrentTextColor() == 0xFF12151C) {
				active = i;
				break;
			}
		}
		if (tag instanceof ArrayList && active >= 0) {
			@SuppressWarnings("unchecked")
			ArrayList<String[]> cats = (ArrayList<String[]>) tag;
			if (active < cats.size()) {
				return cats.get(active)[0];
			}
		}
		return "";
	}

	/** 按入口预设关键词匹配分类下标（含别名回退），匹配不到返回 0（全部） */
	private int presetCatIndex(ArrayList<String[]> cats) {
		if (mPresetCat.length() == 0) {
			return 0;
		}
		for (int i = 1; i < cats.size(); i++) {
			if (cats.get(i)[1].contains(mPresetCat)) {
				return i;
			}
		}
		if ("电视剧".equals(mPresetCat)) {
			for (int i = 1; i < cats.size(); i++) {
				String n = cats.get(i)[1];
				if (n.contains("连续剧") || n.contains("剧集")) {
					return i;
				}
			}
		}
		return 0;
	}

	/* ==================== 列表 ==================== */

	private void loadList() {
		mLoading.setVisibility(View.VISIBLE);
		final int seq = ++mListSeq;
		final String api = mSites.get(mSiteIdx).api;
		final String typeId = curTypeId();
		final int page = mPage;
		final String kw = mKeyword;
		new Thread(new Runnable() {
			@Override
			public void run() {
				String body = api + "?ac=videolist&pg=" + page;
				if (typeId != null && typeId.length() > 0) {
					body += "&t=" + typeId;
				}
				if (kw != null && kw.length() > 0) {
					body += "&wd=" + kw;
				}
				JSONArray list = null;
				int pc = 1;
				try {
					JSONObject j = new JSONObject(TvBoxConfig.fetchText(body, 15000));
					list = j.optJSONArray("list");
					pc = Math.max(1, j.optInt("pagecount", 1));
				} catch (Exception e) {
				}
				if (seq != mListSeq) {
					return;
				}
				final JSONArray fList = list == null ? new JSONArray() : list;
				final int fPc = pc;
				mUi.post(new Runnable() {
					@Override
					public void run() {
						if (seq != mListSeq) {
							return;
						}
						mLoading.setVisibility(View.GONE);
						mList = fList;
						mPageCount = fPc;
						if (mPage > mPageCount) {
							mPage = mPageCount;
						}
						mPageInfo.setText(mPage + " / " + mPageCount);
						mAdapter.notifyDataSetChanged();
						if (fList.length() == 0) {
							Toast.makeText(NetVodActivity.this, "没有找到相关影片", Toast.LENGTH_SHORT).show();
						}
					}
				});
			}
		}, "tvbox-list").start();
	}

	/* ==================== 详情 ==================== */

	private void openDetail(int position) {
		try {
			JSONObject v = mList.getJSONObject(position);
			mVodId = v.optString("vod_id");
			mVodName = v.optString("vod_name");
			mVodPic = v.optString("vod_pic");
			if (mTypeName == null || mTypeName.length() == 0) {
				mTypeName = v.optString("type_name", "其它");
			}
		} catch (Exception e) {
			return;
		}
		mDetailPanel.setVisibility(View.VISIBLE);
		mDetailName.setText(mVodName);
		mDetailMeta.setText("加载中...");
		mDetailIntro.setText("");
		mLineRow.removeAllViews();
		mEpisodes.removeAllViews();
		loadPic(mVodPic, mDetailPic);
		final String api = mSites.get(mSiteIdx).api;
		new Thread(new Runnable() {
			@Override
			public void run() {
				JSONObject detail = null;
				try {
					String body = api + "?ac=videolist&ids=" + mVodId;
					JSONObject j = new JSONObject(TvBoxConfig.fetchText(body, 15000));
					JSONArray list = j.optJSONArray("list");
					if (list != null && list.length() > 0) {
						detail = list.optJSONObject(0);
					}
				} catch (Exception e) {
				}
				final JSONObject fDetail = detail;
				mUi.post(new Runnable() {
					@Override
					public void run() {
						showDetail(fDetail);
					}
				});
			}
		}, "tvbox-detail").start();
	}

	private void showDetail(JSONObject detail) {
		if (detail == null) {
			mDetailMeta.setText("加载失败，请重试");
			return;
		}
		StringBuilder meta = new StringBuilder();
		String year = detail.optString("vod_year");
		String area = detail.optString("vod_area");
		String remarks = detail.optString("vod_remarks");
		if (year.length() > 0) {
			meta.append(year).append("  ");
		}
		if (area.length() > 0) {
			meta.append(area).append("  ");
		}
		if (remarks.length() > 0) {
			meta.append(remarks);
		}
		mDetailMeta.setText(meta.toString());
		String intro = detail.optString("vod_content", detail.optString("vod_blurb"));
		mDetailIntro.setText(intro.replaceAll("<[^>]+>", "").trim());

		// 苹果CMS 分集：vod_play_from/vod_play_url 按 $$$ 分线路，线路内 集名$url#集名$url
		mLines = parseCmsLines(detail.optString("vod_play_from", ""),
				detail.optString("vod_play_url", ""));
		mLineIdx = 0;
		buildLineRow();
		buildEpisodeRow();
	}

	/** 解析 CMS 播放字段（$$$ 分线路 / # 分集 / $ 分集名地址） */
	private static ArrayList<Line> parseCmsLines(String from, String url) {
		ArrayList<Line> lines = new ArrayList<Line>();
		String[] froms = from.split("\\$\\$\\$");
		String[] urls = url.split("\\$\\$\\$");
		for (int i = 0; i < urls.length; i++) {
			Line line = new Line();
			line.name = i < froms.length && froms[i].length() > 0 ? froms[i] : "线路" + (i + 1);
			for (String seg : urls[i].split("#")) {
				seg = seg.trim();
				if (seg.length() == 0) {
					continue;
				}
				int p = seg.indexOf('$');
				if (p > -1) {
					String u = seg.substring(p + 1).trim();
					if (u.startsWith("http://") || u.startsWith("https://")) {
						line.eps.add(new String[] { seg.substring(0, p).trim(), u });
					}
				} else if (seg.startsWith("http://") || seg.startsWith("https://")) {
					line.eps.add(new String[] { "播放", seg });
				}
			}
			if (!line.eps.isEmpty()) {
				lines.add(line);
			}
		}
		return lines;
	}

	private void buildLineRow() {
		mLineRow.removeAllViews();
		for (int i = 0; i < mLines.size(); i++) {
			final int idx = i;
			Button b = makeChip(mLines.get(i).name, i == mLineIdx);
			b.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					mLineIdx = idx;
					buildLineRow();
					buildEpisodeRow();
				}
			});
			mLineRow.addView(b);
		}
	}

	private void buildEpisodeRow() {
		mEpisodes.removeAllViews();
		if (mLines == null || mLines.isEmpty()) {
			TextView tv = new TextView(this);
			tv.setText("该影片暂无可播放地址");
			tv.setTextColor(0xFF9AA1AC);
			mEpisodes.addView(tv);
			return;
		}
		ArrayList<String[]> eps = mLines.get(mLineIdx).eps;
		int perRow = 5;
		for (int start = 0; start < eps.size(); start += perRow) {
			LinearLayout row = new LinearLayout(this);
			row.setOrientation(LinearLayout.HORIZONTAL);
			int end = Math.min(start + perRow, eps.size());
			for (int i = start; i < end; i++) {
				final int idx = i;
				Button b = new Button(this);
				b.setText(eps.get(i)[0]);
				b.setTextSize(13);
				b.setPadding(16, 4, 16, 4);
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
						ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
				lp.rightMargin = 6;
				lp.topMargin = 6;
				b.setLayoutParams(lp);
				b.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						playAt(idx);
					}
				});
				row.addView(b);
			}
			mEpisodes.addView(row);
		}
	}

	private void playAt(int idx) {
		if (mLines == null || mLines.isEmpty()) {
			return;
		}
		ArrayList<String[]> eps = mLines.get(mLineIdx).eps;
		if (idx < 0 || idx >= eps.size()) {
			return;
		}
		ArrayList<VideoInfo> infos = new ArrayList<VideoInfo>();
		for (String[] ep : eps) {
			VideoInfo info = new VideoInfo();
			info.title = ep[0];
			info.url = ep[1];
			infos.add(info);
		}
		String raw = eps.get(idx)[1];
		String proxied = VideoList.getProxiedUrl(raw);
		Intent it = new Intent();
		if (proxied.equals(raw)) {
			it.setClass(this, NetVodPlayerActivity.class);
		} else {
			// VIP 站点网页地址 -> XWalk 网页播放
			for (VideoInfo info : infos) {
				info.url = VideoList.getProxiedUrl(info.url);
			}
			it.setClass(this, WebVideoPlayerActivity.class);
		}
		it.putParcelableArrayListExtra("videoinfo", infos);
		it.putExtra("albumPic", mVodPic);
		it.putExtra("vodtype", mTypeName == null || mTypeName.length() == 0 ? "其它" : mTypeName);
		it.putExtra("videoId", mVodId);
		it.putExtra("vodname", mVodName);
		it.putExtra("sourceId", mSites.get(mSiteIdx).name);
		it.putExtra("playIndex", idx);
		it.putExtra("collectionTime", 0);
		startActivity(it);
	}

	/* ==================== 海报加载 ==================== */

	private void loadPic(final String url, final ImageView view) {
		if (url == null || url.length() == 0) {
			view.setImageBitmap(null);
			return;
		}
		Bitmap cached = mPicCache.get(url);
		if (cached != null) {
			view.setImageBitmap(cached);
			return;
		}
		view.setImageBitmap(null);
		view.setTag(url);
		new Thread(new Runnable() {
			@Override
			public void run() {
				Bitmap bmp = null;
				try {
					byte[] data = TvBoxConfig.fetchBytes(url, 10000);
					if (data != null) {
						bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
					}
				} catch (Exception e) {
				}
				if (bmp != null) {
					mPicCache.put(url, bmp);
				}
				final Bitmap fBmp = bmp;
				mUi.post(new Runnable() {
					@Override
					public void run() {
						if (url.equals(view.getTag())) {
							view.setImageBitmap(fBmp);
						}
					}
				});
			}
		}, "tvbox-pic").start();
	}

	/* ==================== 列表适配器 ==================== */

	private class GridAdapter extends BaseAdapter {
		@Override
		public int getCount() {
			return mList.length();
		}

		@Override
		public Object getItem(int position) {
			return null;
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View v = convertView;
			if (v == null) {
				v = LayoutInflater.from(NetVodActivity.this).inflate(R.layout.net_vod_item, parent, false);
			}
			JSONObject item = mList.optJSONObject(position);
			if (item == null) {
				return v;
			}
			ImageView pic = (ImageView) v.findViewById(R.id.net_item_pic);
			TextView name = (TextView) v.findViewById(R.id.net_item_name);
			TextView remarks = (TextView) v.findViewById(R.id.net_item_remarks);
			name.setText(item.optString("vod_name"));
			remarks.setText(item.optString("vod_remarks"));
			loadPic(item.optString("vod_pic"), pic);
			return v;
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && mDetailPanel.getVisibility() == View.VISIBLE) {
			mDetailPanel.setVisibility(View.GONE);
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}
}
