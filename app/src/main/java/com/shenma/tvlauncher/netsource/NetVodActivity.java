package com.shenma.tvlauncher.netsource;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
import android.widget.RadioButton;
import android.widget.RadioGroup;
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

	private LinearLayout mSiteRow, mCatRow;
	private GridView mGrid;
	private TextView mPageInfo, mLoading, mDetailName, mDetailIntro, mTitle;
	private TextView mDetailDirector, mDetailActors, mDetailArea, mDetailYear, mDetailType, mDetailRemarks;
	private ImageView mDetailPic;
	private RadioGroup mDetailSources;
	private GridView mDetailEpisodes;
	private View mDetailPanel, mFilterPanel;
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

	// 多站点聚合：每站分类列表 + 每站当前选中的 type_id
	@SuppressWarnings("unchecked")
	private ArrayList<String[]>[] mAllCats;
	private String[] mSiteTypeIds;
	private ArrayList<String[]> mDisplayCats;
	private String mCurCatName = "全部";

	private int mListSeq = 0, mCatSeq = 0;

	private String mPresetCat = "";// 入口预设分类关键词（如 电影/电视剧），匹配不到则回退"全部"

	private String mPresetSite = null;// 入口预设站点名（搜索结果跳转），按名字选中对应站点

	private String mOpenVodId = null;// 入口指定影片 id（首页推荐位/搜索结果），自动打开详情

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
		String ovi = getIntent().getStringExtra("openVodId");
		if (ovi != null) {
			mOpenVodId = ovi;
		}
		mPresetSite = getIntent().getStringExtra("presetSite");
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
		setBtn(R.id.net_menu_btn, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mFilterPanel.getVisibility() == View.VISIBLE) {
					mFilterPanel.setVisibility(View.GONE);
				} else {
					mFilterPanel.setVisibility(View.VISIBLE);
				}
			}
		});
		setBtn(R.id.net_spider_btn, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startActivity(new Intent(NetVodActivity.this, SpiderVodActivity.class));
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
						// 搜索结果直达：按预设站点名定位 mSiteIdx，供 openDetailById 使用
						if (mPresetSite != null) {
							for (int i = 0; i < sites.size(); i++) {
								if (sites.get(i).name.equals(mPresetSite)) {
									mSiteIdx = i;
									break;
								}
							}
						}
						initAllSites();
						// 搜索结果直达：不依赖列表页，直接按 id 拉详情打开面板
						if (mPresetSite != null && mOpenVodId != null) {
							String id = mOpenVodId;
							mOpenVodId = null;
							openDetailById(id);
						}
					}
				});
			}
		}, "tvbox-config-init").start();
	}

	private void findViews() {
		mSiteRow = (LinearLayout) findViewById(R.id.net_site_row);
		mCatRow = (LinearLayout) findViewById(R.id.net_cat_row);
		mDetailSources = (RadioGroup) findViewById(R.id.net_detail_sources);
		mDetailEpisodes = (GridView) findViewById(R.id.net_detail_episodes);
		mGrid = (GridView) findViewById(R.id.net_vod_grid);
		mPageInfo = (TextView) findViewById(R.id.net_page_info);
		mLoading = (TextView) findViewById(R.id.net_vod_loading);
		mDetailPanel = findViewById(R.id.net_detail_panel);
		mFilterPanel = findViewById(R.id.net_filter_panel);
		mDetailName = (TextView) findViewById(R.id.net_detail_name);
		mDetailDirector = (TextView) findViewById(R.id.net_detail_director);
		mDetailActors = (TextView) findViewById(R.id.net_detail_actors);
		mDetailArea = (TextView) findViewById(R.id.net_detail_area);
		mDetailYear = (TextView) findViewById(R.id.net_detail_year);
		mDetailType = (TextView) findViewById(R.id.net_detail_type);
		mDetailRemarks = (TextView) findViewById(R.id.net_detail_remarks);
		mDetailIntro = (TextView) findViewById(R.id.net_detail_intro);
		mDetailPic = (ImageView) findViewById(R.id.net_detail_pic);
		mSearchInput = (EditText) findViewById(R.id.net_search_input);
		mTitle = (TextView) findViewById(R.id.net_title);
		if (mPresetCat != null && mPresetCat.length() > 0) {
			mTitle.setText(mPresetCat);
		}
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

	/* ==================== 多站点聚合初始化 ==================== */

	/** 并行加载所有站点分类，按 presetCat 匹配每站 type_id，并构建展示分类行 */
	@SuppressWarnings("unchecked")
	private void initAllSites() {
		int n = mSites.size();
		mAllCats = (ArrayList<String[]>[]) new ArrayList[n];
		mSiteTypeIds = new String[n];
		for (int i = 0; i < n; i++) {
			mSiteTypeIds[i] = "";// 默认"全部"（空 type_id）
		}
		// 站点行仍然构建，仅在菜单展开时可见
		buildSiteRow();
		mLoading.setVisibility(View.VISIBLE);
		final int seq = ++mCatSeq;
		final CountDownLatch latch = new CountDownLatch(n);
		final int perTimeout = 8000;
		for (int i = 0; i < n; i++) {
			final int idx = i;
			final String api = mSites.get(i).api;
			new Thread(new Runnable() {
				@Override
				public void run() {
					ArrayList<String[]> catList = new ArrayList<String[]>();
					catList.add(new String[] { "", "全部" });
					try {
						JSONObject j = new JSONObject(TvBoxConfig.fetchText(api + "?ac=list", perTimeout));
						JSONArray cls = j.optJSONArray("class");
						if (cls != null) {
							for (int k = 0; k < cls.length(); k++) {
								JSONObject c = cls.optJSONObject(k);
								if (c == null) {
									continue;
								}
								catList.add(new String[] { c.optString("type_id"), c.optString("type_name") });
							}
						}
					} catch (Exception e) {
					}
					synchronized (mAllCats) {
						mAllCats[idx] = catList;
					}
					latch.countDown();
				}
			}, "tvbox-cats-" + idx).start();
		}
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					latch.await(12, TimeUnit.SECONDS);
				} catch (Exception e) {
				}
				if (seq != mCatSeq) {
					return;
				}
				// 选取分类数最多的站点作为展示基准
				int displayIdx = 0;
				int maxCats = 0;
				for (int i = 0; i < mAllCats.length; i++) {
					if (mAllCats[i] != null && mAllCats[i].size() > maxCats) {
						maxCats = mAllCats[i].size();
						displayIdx = i;
					}
				}
				final ArrayList<String[]> displayCats = mAllCats[displayIdx];
				// 每站按 presetCat 匹配 type_id
				for (int i = 0; i < mAllCats.length; i++) {
					mSiteTypeIds[i] = matchTypeId(mAllCats[i], mPresetCat);
				}
				mCurCatName = mPresetCat.length() > 0 ? mPresetCat : "全部";
				final int fActiveIdx = presetCatIndex(displayCats);
				mUi.post(new Runnable() {
					@Override
					public void run() {
						if (seq != mCatSeq) {
							return;
						}
						mDisplayCats = displayCats;
						mTypeName = displayCats.get(fActiveIdx)[1];
						buildCatRow(displayCats, fActiveIdx);
						loadList();
					}
				});
			}
		}, "tvbox-cats-wait").start();
	}

	/** 按分类名称在某站分类列表中匹配 type_id（含别名回退），匹配不到返回""（全部） */
	private String matchTypeId(ArrayList<String[]> cats, String catName) {
		if (cats == null || catName == null || catName.length() == 0) {
			return "";
		}
		for (String[] c : cats) {
			if (c[1].contains(catName)) {
				return c[0];
			}
		}
		// 别名回退
		if ("电视剧".equals(catName)) {
			for (String[] c : cats) {
				if (c[1].contains("连续剧") || c[1].contains("剧集")) {
					return c[0];
				}
			}
		}
		return "";// 无匹配：用"全部"
	}

	private void buildSiteRow() {
		mSiteRow.removeAllViews();
		for (int i = 0; i < mSites.size(); i++) {
			final int idx = i;
			Button b = makeChip(mSites.get(i).name, i == mSiteIdx);
			b.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (idx != mSiteIdx) {
						mSiteIdx = idx;
						mPage = 1;
						buildSiteRow();
					}
				}
			});
			mSiteRow.addView(b);
		}
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
		// 更新视觉选中状态
		for (int i = 0; i < mCatRow.getChildCount(); i++) {
			View v = mCatRow.getChildAt(i);
			boolean active = (i == activeIdx);
			v.setBackgroundColor(active ? 0xFF4FC3F7 : 0xFF2A313D);
			((TextView) v).setTextColor(active ? 0xFF12151C : 0xFFE6E9EE);
		}
		// 更新每站 type_id：按所选分类名跨站匹配
		String catName = cats.get(activeIdx)[1];
		mCurCatName = catName;
		if (mAllCats != null && mSiteTypeIds != null) {
			for (int i = 0; i < mAllCats.length; i++) {
				mSiteTypeIds[i] = matchTypeId(mAllCats[i], catName);
			}
		}
		loadList();
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

	/** 并行查询所有站点，合并结果，每条注入 _site_idx 以便详情时回溯来源 API */
	private void loadList() {
		mLoading.setVisibility(View.VISIBLE);
		final int seq = ++mListSeq;
		final int n = mSites.size();
		final int page = mPage;
		final String kw = mKeyword;
		final ArrayList<JSONObject> merged = new ArrayList<JSONObject>();
		final int[] pagecounts = new int[n];
		for (int i = 0; i < n; i++) {
			pagecounts[i] = 1;
		}
		final CountDownLatch latch = new CountDownLatch(n);
		for (int i = 0; i < n; i++) {
			final int idx = i;
			final String api = mSites.get(i).api;
			final String typeId = (mSiteTypeIds != null && idx < mSiteTypeIds.length) ? mSiteTypeIds[idx] : "";
			new Thread(new Runnable() {
				@Override
				public void run() {
					StringBuilder url = new StringBuilder(api).append("?ac=videolist&pg=").append(page);
					if (typeId != null && typeId.length() > 0) {
						url.append("&t=").append(typeId);
					}
					if (kw != null && kw.length() > 0) {
						try {
							url.append("&wd=").append(URLEncoder.encode(kw, "UTF-8"));
						} catch (Exception e) {
							url.append("&wd=").append(kw);
						}
					}
					try {
						JSONObject j = new JSONObject(TvBoxConfig.fetchText(url.toString(), 8000));
						JSONArray list = j.optJSONArray("list");
						int pc = Math.max(1, j.optInt("pagecount", 1));
						synchronized (merged) {
							pagecounts[idx] = pc;
							if (list != null) {
								for (int k = 0; k < list.length(); k++) {
									JSONObject it = list.optJSONObject(k);
									if (it == null) {
										continue;
									}
									try {
										it.put("_site_idx", idx);
									} catch (Exception e) {
									}
									merged.add(it);
								}
							}
						}
					} catch (Exception e) {
					}
					latch.countDown();
				}
			}, "tvbox-list-" + idx).start();
		}
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					latch.await(12, TimeUnit.SECONDS);
				} catch (Exception e) {
				}
				if (seq != mListSeq) {
					return;
				}
				int maxPc = 1;
				for (int pc : pagecounts) {
					if (pc > maxPc) {
						maxPc = pc;
					}
				}
				final JSONArray fList = new JSONArray();
				for (JSONObject it : merged) {
					fList.put(it);
				}
				final int fPc = maxPc;
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
						// 首页推荐位进入：自动打开指定影片详情（仅一次）
						if (mOpenVodId != null) {
							String want = mOpenVodId;
							mOpenVodId = null;
							for (int k = 0; k < fList.length(); k++) {
								JSONObject it = fList.optJSONObject(k);
								if (it != null && want.equals(it.optString("vod_id"))) {
									openDetail(k);
									break;
								}
							}
						}
					}
				});
			}
		}, "tvbox-list-wait").start();
	}

	/* ==================== 详情 ==================== */

	private void openDetail(int position) {
		try {
			JSONObject v = mList.getJSONObject(position);
			mVodId = v.optString("vod_id");
			mVodName = v.optString("vod_name");
			mVodPic = v.optString("vod_pic");
			// 从列表项读取来源站点下标（并行 loadList 注入），定位详情 API
			int sidx = v.optInt("_site_idx", mSiteIdx);
			if (sidx >= 0 && sidx < mSites.size()) {
				mSiteIdx = sidx;
			}
			if (mTypeName == null || mTypeName.length() == 0) {
				mTypeName = v.optString("type_name", "其它");
			}
		} catch (Exception e) {
			return;
		}
		mDetailPanel.setVisibility(View.VISIBLE);
		mDetailName.setText(mVodName);
		mDetailDirector.setText("");
		mDetailActors.setText("");
		mDetailArea.setText("");
		mDetailYear.setText("");
		mDetailType.setText("");
		mDetailRemarks.setText("");
		mDetailIntro.setText("");
		mDetailSources.removeAllViews();
		mDetailEpisodes.setAdapter(null);
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

	/** 按影片 id 直接打开详情面板（搜索跳转，目标片可能不在当前列表页） */
	private void openDetailById(final String vodId) {
		mVodId = vodId;
		mVodName = "";
		mVodPic = "";
		mDetailPanel.setVisibility(View.VISIBLE);
		mDetailName.setText("加载中...");
		mDetailDirector.setText("");
		mDetailActors.setText("");
		mDetailArea.setText("");
		mDetailYear.setText("");
		mDetailType.setText("");
		mDetailRemarks.setText("");
		mDetailIntro.setText("");
		mDetailSources.removeAllViews();
		mDetailEpisodes.setAdapter(null);
		final String api = mSites.get(mSiteIdx).api;
		new Thread(new Runnable() {
			@Override
			public void run() {
				JSONObject detail = null;
				try {
					String body = api + "?ac=videolist&ids=" + vodId;
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
						if (fDetail == null) {
							mDetailName.setText("详情加载失败");
							return;
						}
						mVodName = fDetail.optString("vod_name");
						mVodPic = fDetail.optString("vod_pic");
						mDetailName.setText(mVodName);
						loadPic(mVodPic, mDetailPic);
						showDetail(fDetail);
					}
				});
			}
		}, "tvbox-detail-direct").start();
	}

	private void showDetail(JSONObject detail) {
		if (detail == null) {
			mDetailDirector.setText("加载失败，请重试");
			return;
		}
		mDetailDirector.setText("导演：" + detail.optString("vod_director"));
		mDetailActors.setText("主演：" + detail.optString("vod_actor"));
		mDetailArea.setText("地区：" + detail.optString("vod_area"));
		mDetailYear.setText("年代：" + detail.optString("vod_year"));
		mDetailType.setText("类型：" + detail.optString("vod_class"));
		mDetailRemarks.setText("备注：" + detail.optString("vod_remarks"));
		String intro = detail.optString("vod_content", detail.optString("vod_blurb"));
		mDetailIntro.setText(intro.replaceAll("<[^>]+>", "").trim());

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
		mDetailSources.removeAllViews();
		if (mLines == null || mLines.isEmpty()) {
			return;
		}
		for (int i = 0; i < mLines.size(); i++) {
			final int idx = i;
			RadioButton rb = new RadioButton(this);
			rb.setText(mLines.get(i).name);
			rb.setTextColor(0xFFFFFFFF);
			rb.setButtonDrawable(null);
			rb.setPadding(16, 4, 16, 4);
			rb.setGravity(Gravity.CENTER);
			rb.setBackgroundColor(i == mLineIdx ? 0xFF4FC3F7 : 0xFF2A313D);
			rb.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					mLineIdx = idx;
					buildLineRow();
					buildEpisodeRow();
				}
			});
			RadioGroup.LayoutParams lp = new RadioGroup.LayoutParams(
					ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			lp.rightMargin = 8;
			mDetailSources.addView(rb, lp);
		}
	}

	private void buildEpisodeRow() {
		if (mLines == null || mLines.isEmpty()) {
			mDetailEpisodes.setAdapter(null);
			return;
		}
		mDetailEpisodes.setAdapter(new EpisodeAdapter());
	}

	private class EpisodeAdapter extends BaseAdapter {
		@Override
		public int getCount() {
			if (mLines == null || mLines.isEmpty()) {
				return 0;
			}
			return mLines.get(mLineIdx).eps.size();
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
			TextView tv;
			if (convertView instanceof TextView) {
				tv = (TextView) convertView;
			} else {
				tv = new TextView(NetVodActivity.this);
				tv.setPadding(12, 8, 12, 8);
				tv.setTextColor(0xFFFFFFFF);
				tv.setTextSize(13);
				tv.setGravity(Gravity.CENTER);
				tv.setBackgroundColor(0xFF2A313D);
			}
			tv.setText(mLines.get(mLineIdx).eps.get(position)[0]);
			return tv;
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
