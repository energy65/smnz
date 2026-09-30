package com.shenma.tvlauncher.netsource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;

import com.github.catvod.crawler.Spider;
import com.shenma.tvlauncher.R;
import com.shenma.tvlauncher.spider.SpiderApi;
import com.shenma.tvlauncher.spider.SpiderEngine;
import com.shenma.tvlauncher.spider.SpiderSite;
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
 * 网络点播聚合页。
 * 只使用 type=3 爬虫源（type=1 CMS 接口源已下线）。
 * - 站点行首项为「全部源」聚合模式：并行查询所有爬虫源，按归一化片名合并同一部影片，
 *   一部影片只显示一条记录，卡片角标显示线路数。
 * - 详情页线路行按「源名·线路名」列出该片在所有源上的全部线路，切换线路即切换剧集。
 * - 播放：地址以 http 开头直接播放，否则交给所属源的 playerContent 解析，
 *   再按地址形态决定走原生播放器还是 XWalk 网页播放器。
 */
public class NetVodActivity extends Activity {

	/** 聚合模式下代表"全部源"的站点下标 */
	private static final int SITE_ALL = -1;
	private static final long LIST_TIMEOUT_MS = 15000L;
	private static final long DETAIL_TIMEOUT_MS = 15000L;

	/** 详情页的一条线路：某源上的某条播放线路 */
	private static class Line {
		String siteKey = "";
		String siteName = "";
		String flag = "";
		ArrayList<SpiderApi.Item> items = new ArrayList<SpiderApi.Item>();
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

	private ArrayList<SpiderSite> mSites = new ArrayList<SpiderSite>();
	private ArrayList<Spider> mSpiders = new ArrayList<Spider>();
	private int mSiteIdx = SITE_ALL;
	private int mPage = 1;
	private int mPageCount = 1;
	private String mKeyword = "";
	private ArrayList<VodFilm> mFilms = new ArrayList<VodFilm>();
	private GridAdapter mAdapter;

	// 每站分类（type_id, type_name）
	private ArrayList<ArrayList<String[]>> mSiteCats = new ArrayList<ArrayList<String[]>>();
	private ArrayList<String[]> mDisplayCats = new ArrayList<String[]>();
	private String mCurCatName = "全部";
	private String mTypeName = "全部";

	// 详情状态
	private ArrayList<Line> mLines = new ArrayList<Line>();
	private int mLineIdx = 0;
	private String mVodName = "", mVodPic = "";

	private String mPresetCat = "";
	private String mOpenVodTitle = null;
	private int mListSeq = 0, mCatSeq = 0;

	private LruCache<String, Bitmap> mPicCache = new LruCache<String, Bitmap>(96);
	private final Handler mUi = new Handler();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.net_vod_main);
		String pc = getIntent().getStringExtra("presetCat");
		if (pc != null) {
			mPresetCat = pc;
		}
		mOpenVodTitle = getIntent().getStringExtra("openVodTitle");
		findViews();
		mAdapter = new GridAdapter();
		mGrid.setAdapter(mAdapter);
		mGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				openDetail(position);
			}
		});
		mDetailEpisodes.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				playAt(position);
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
		setBtn(R.id.net_detail_play, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				playAt(0);
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
				loadSites(true);
			}
		});
		loadSites(false);
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
		TextView refresh = (TextView) findViewById(R.id.net_spider_btn);
		if (refresh != null) {
			refresh.setText("刷新源");
		}
		mTitle.setText(mPresetCat.length() > 0 ? mPresetCat : "网络点播");
		mFilterPanel.setVisibility(View.VISIBLE);
	}

	private void setBtn(int id, View.OnClickListener l) {
		View v = findViewById(id);
		if (v != null) {
			v.setOnClickListener(l);
		}
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

	/* ==================== 源加载 ==================== */

	/** 加载全部爬虫源，并为每站取回分类 */
	private void loadSites(boolean forceReload) {
		mLoading.setVisibility(View.VISIBLE);
		mLoading.setText("正在加载片源…");
		new Thread(new Runnable() {
			@Override
			public void run() {
				final ArrayList<SpiderSite> sites = TvBoxConfig.getSpiders(NetVodActivity.this);
				final ArrayList<Spider> spiders = new ArrayList<Spider>();
				if (sites != null) {
					for (int i = 0; i < sites.size(); i++) {
						Spider sp = null;
						try {
							sp = SpiderEngine.get().getSpider(sites.get(i));
						} catch (Exception e) {
							sp = null;
						}
						spiders.add(sp);
					}
				}
				mUi.post(new Runnable() {
					@Override
					public void run() {
						mLoading.setText("正在加载片源…");
						if (sites == null || sites.isEmpty()) {
							Toast.makeText(NetVodActivity.this, "片源加载失败，请检查网络或配置", Toast.LENGTH_LONG).show();
							finish();
							return;
						}
						mSites = sites;
						mSpiders = spiders;
						mSiteIdx = SITE_ALL;
						buildSiteRow();
						loadCategories();
					}
				});
			}
		}, "vod-sites").start();
	}

	private void buildSiteRow() {
		mSiteRow.removeAllViews();
		Button all = makeChip("全部源", mSiteIdx == SITE_ALL);
		all.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (mSiteIdx != SITE_ALL) {
					mSiteIdx = SITE_ALL;
					mPage = 1;
					mKeyword = "";
					mSearchInput.setText("");
					buildSiteRow();
					loadCategories();
				}
			}
		});
		mSiteRow.addView(all);
		for (int i = 0; i < mSites.size(); i++) {
			final int idx = i;
			SpiderSite s = mSites.get(i);
			Button b = makeChip(s.name + kindTag(s), i == mSiteIdx);
			b.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (idx != mSiteIdx) {
						mSiteIdx = idx;
						mPage = 1;
						mKeyword = "";
						mSearchInput.setText("");
						buildSiteRow();
						loadCategories();
					}
				}
			});
			mSiteRow.addView(b);
		}
	}

	private String kindTag(SpiderSite s) {
		if (s.isPy()) {
			return "  PY";
		}
		if (s.isJs()) {
			return "  JS";
		}
		return "";
	}

	/* ==================== 分类 ==================== */

	/** 并行取回参与聚合的各源分类，合并出展示分类行 */
	private void loadCategories() {
		mLoading.setVisibility(View.VISIBLE);
		mLoading.setText("正在加载分类…");
		final int seq = ++mCatSeq;
		final int from = mSiteIdx == SITE_ALL ? 0 : mSiteIdx;
		final int to = mSiteIdx == SITE_ALL ? mSites.size() : mSiteIdx + 1;
		final int n = to - from;
		if (n <= 0) {
			mLoading.setVisibility(View.GONE);
			return;
		}
		final ArrayList<ArrayList<String[]>> cats = new ArrayList<ArrayList<String[]>>();
		for (int i = 0; i < n; i++) {
			cats.add(null);
		}
		final CountDownLatch latch = new CountDownLatch(n);
		for (int i = 0; i < n; i++) {
			final int slot = i;
			final int siteIdx = from + i;
			new Thread(new Runnable() {
				@Override
				public void run() {
					ArrayList<String[]> list = new ArrayList<String[]>();
					list.add(new String[] { "", "全部" });
					Spider sp = (siteIdx >= 0 && siteIdx < mSpiders.size()) ? mSpiders.get(siteIdx) : null;
					if (sp != null) {
						try {
							JSONArray arr = SpiderApi.homeClasses(sp, true);
							for (int k = 0; k < arr.length(); k++) {
								JSONObject c = arr.optJSONObject(k);
								if (c == null) {
									continue;
								}
								String tid = c.optString("type_id");
								String tname = c.optString("type_name");
								if (tname.length() > 0) {
									list.add(new String[] { tid, tname });
								}
							}
						} catch (Exception e) {
						}
					}
					synchronized (cats) {
						cats.set(slot, list);
					}
					latch.countDown();
				}
			}, "vod-cats-" + siteIdx).start();
		}
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					latch.await(LIST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
				} catch (Exception e) {
				}
				if (seq != mCatSeq) {
					return;
				}
				final ArrayList<ArrayList<String[]>> fCats = new ArrayList<ArrayList<String[]>>(cats);
				mUi.post(new Runnable() {
					@Override
					public void run() {
						if (seq != mCatSeq) {
							return;
						}
						mSiteCats = fCats;
						mDisplayCats = mergeCats(fCats, mSiteIdx == SITE_ALL);
						int active = matchCatIndex(mDisplayCats, mPresetCat);
						mCurCatName = mDisplayCats.get(active)[1];
						buildCatRow(active);
						loadList();
					}
				});
			}
		}, "vod-cats-wait").start();
	}

	/**
	 * 合并分类行。聚合模式下把所有源的同名分类并成一项（type_id 留空，查询时按名字逐站匹配）。
	 */
	private ArrayList<String[]> mergeCats(ArrayList<ArrayList<String[]>> cats, boolean aggregate) {
		ArrayList<String[]> out = new ArrayList<String[]>();
		out.add(new String[] { "", "全部" });
		if (!aggregate) {
			if (cats != null && !cats.isEmpty() && cats.get(0) != null) {
				for (String[] c : cats.get(0)) {
					out.add(c);
				}
			}
			return out;
		}
		HashSet<String> seen = new HashSet<String>();
		seen.add("全部");
		if (cats == null) {
			return out;
		}
		for (ArrayList<String[]> perSite : cats) {
			if (perSite == null) {
				continue;
			}
			for (String[] c : perSite) {
				String name = c[1];
				if (name.length() == 0) {
					continue;
				}
				String canon = canonCat(name);
				if (seen.add(canon)) {
					// 聚合项的 type_id 留空，loadList 时按分类名逐站匹配
					out.add(new String[] { "", name });
				}
			}
		}
		return out;
	}

	/** 分类名归一，用于跨源合并同一分类（如 电影 / 影视 / 电影片） */
	private String canonCat(String name) {
		String s = name.replace("片", "").replace("剧", "").replace("集", "");
		s = s.replace("连续", "").replace("综艺", "综艺");
		return s.replace(" ", "").replace("　", "");
	}

	/** 按名称（含别名）在分类行中定位下标，找不到返回 0（全部） */
	private int matchCatIndex(ArrayList<String[]> cats, String want) {
		if (want == null || want.length() == 0 || cats == null || cats.isEmpty()) {
			return 0;
		}
		for (int i = 1; i < cats.size(); i++) {
			if (cats.get(i)[1].contains(want) || canonCat(cats.get(i)[1]).contains(canonCat(want))) {
				return i;
			}
		}
		if ("电视剧".equals(want)) {
			for (int i = 1; i < cats.size(); i++) {
				String n = cats.get(i)[1];
				if (n.contains("连续剧") || n.contains("剧集")) {
					return i;
				}
			}
		}
		if ("电影".equals(want)) {
			for (int i = 1; i < cats.size(); i++) {
				if (cats.get(i)[1].contains("影视")) {
					return i;
				}
			}
		}
		return 0;
	}

	private void buildCatRow(final int activeIdx) {
		mCatRow.removeAllViews();
		for (int i = 0; i < mDisplayCats.size(); i++) {
			final int idx = i;
			Button b = makeChip(mDisplayCats.get(i)[1], i == activeIdx);
			b.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					mCurCatName = mDisplayCats.get(idx)[1];
					mTypeName = mCurCatName;
					mPage = 1;
					buildCatRow(idx);
					loadList();
				}
			});
			mCatRow.addView(b);
		}
	}

	/** 在某站分类里按名字找 type_id，聚合模式逐站匹配；找不到返回空串（该站用推荐） */
	private String typeIdOf(int siteSlot, String catName) {
		if (catName == null || catName.length() == 0 || "全部".equals(catName)) {
			return "";
		}
		ArrayList<String[]> perSite = (siteSlot >= 0 && siteSlot < mSiteCats.size()) ? mSiteCats.get(siteSlot) : null;
		if (perSite == null) {
			return "";
		}
		String want = canonCat(catName);
		for (String[] c : perSite) {
			if (c[1].equals(catName)) {
				return c[0];
			}
		}
		for (String[] c : perSite) {
			if (canonCat(c[1]).equals(want)) {
				return c[0];
			}
		}
		return "";
	}

	/* ==================== 列表聚合 ==================== */

	/** 并行查询参与聚合的各源，按归一化片名合并为一部影片一条记录 */
	private void loadList() {
		mLoading.setVisibility(View.VISIBLE);
		mLoading.setText("加载中…");
		final int seq = ++mListSeq;
		final int from = mSiteIdx == SITE_ALL ? 0 : mSiteIdx;
		final int to = mSiteIdx == SITE_ALL ? mSites.size() : mSiteIdx + 1;
		final int n = to - from;
		if (n <= 0) {
			mLoading.setVisibility(View.GONE);
			return;
		}
		final String kw = mKeyword;
		final String cat = mCurCatName;
		final int page = mPage;
		final Map<String, VodFilm> films = VodFilm.newMap();
		final int[] pagecounts = new int[n];
		for (int i = 0; i < n; i++) {
			pagecounts[i] = 1;
		}
		final CountDownLatch latch = new CountDownLatch(n);
		for (int i = 0; i < n; i++) {
			final int slot = i;
			final int siteIdx = from + i;
			new Thread(new Runnable() {
				@Override
				public void run() {
					Spider sp = (siteIdx >= 0 && siteIdx < mSpiders.size()) ? mSpiders.get(siteIdx) : null;
					if (sp == null) {
						latch.countDown();
						return;
					}
					SpiderSite site = mSites.get(siteIdx);
					JSONObject ret = null;
					try {
						if (kw != null && kw.length() > 0) {
							ret = SpiderApi.search(sp, kw, false);
						} else {
							String tid = typeIdOf(slot, cat);
							if (tid != null && tid.length() > 0) {
								ret = SpiderApi.category(sp, tid, String.valueOf(page), false, null);
							} else if ("全部".equals(cat)) {
								ret = SpiderApi.homeVideo(sp);
							} else {
								// 该站没有此分类，用推荐兜底
								ret = SpiderApi.homeVideo(sp);
							}
						}
					} catch (Exception e) {
						ret = null;
					}
					JSONArray list = ret == null ? null : ret.optJSONArray("list");
					int pc = ret == null ? 1 : Math.max(1, ret.optInt("pagecount", 1));
					synchronized (films) {
						pagecounts[slot] = pc;
						if (list != null) {
							for (int k = 0; k < list.length(); k++) {
								JSONObject v = list.optJSONObject(k);
								VodFilm.put(films, v, site.key, site.name);
							}
						}
					}
					latch.countDown();
				}
			}, "vod-list-" + siteIdx).start();
		}
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					latch.await(LIST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
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
				final ArrayList<VodFilm> merged = new ArrayList<VodFilm>(films.values());
				final int fPc = maxPc;
				mUi.post(new Runnable() {
					@Override
					public void run() {
						if (seq != mListSeq) {
							return;
						}
						mLoading.setVisibility(View.GONE);
						mFilms = merged;
						mPageCount = fPc;
						if (mPage > mPageCount) {
							mPage = mPageCount;
						}
						mPageInfo.setText(mPage + " / " + mPageCount);
						mAdapter.notifyDataSetChanged();
						if (merged.isEmpty()) {
							Toast.makeText(NetVodActivity.this, "没有找到相关影片", Toast.LENGTH_SHORT).show();
						}
						// 首页推荐位进入：按片名搜索并自动打开第一个匹配
						if (mOpenVodTitle != null) {
							String want = VodFilm.normalize(mOpenVodTitle);
							mOpenVodTitle = null;
							for (int k = 0; k < merged.size(); k++) {
								if (want.equals(VodFilm.normalize(merged.get(k).title))) {
									openDetail(k);
									break;
								}
							}
						}
					}
				});
			}
		}, "vod-list-wait").start();
	}

	/* ==================== 详情：多线路 ==================== */

	private void openDetail(int position) {
		if (position < 0 || position >= mFilms.size()) {
			return;
		}
		final VodFilm film = mFilms.get(position);
		mVodName = film.title;
		mVodPic = film.pic;
		mLines = new ArrayList<Line>();
		mLineIdx = 0;
		buildLineRow();
		buildEpisodeGrid();
		// 先用列表项里已有的元信息填充，随后用详情覆盖
		mDetailName.setText(film.title);
		mDetailDirector.setText("");
		mDetailActors.setText("");
		mDetailArea.setText("");
		mDetailYear.setText(film.year == null || film.year.length() == 0 ? "" : "年代：" + film.year);
		mDetailType.setText("");
		mDetailRemarks.setText("线路：" + film.lineCount() + " 个源");
		mDetailIntro.setText("");
		loadPic(film.pic, mDetailPic);
		mDetailPanel.setVisibility(View.VISIBLE);
		mLoading.setText("正在获取线路…");

		final int n = film.refs.size();
		final CountDownLatch latch = new CountDownLatch(n);
		final ArrayList<Line> collected = new ArrayList<Line>();
		final JSONObject[] firstDetail = new JSONObject[1];
		for (int i = 0; i < n; i++) {
			final VodFilm.Ref ref = film.refs.get(i);
			new Thread(new Runnable() {
				@Override
				public void run() {
					Spider sp = spiderOf(ref.siteKey);
					if (sp != null && ref.vodId != null && ref.vodId.length() > 0) {
						try {
							JSONObject detail = SpiderApi.detail(sp, ref.vodId);
							ArrayList<SpiderApi.Line> sl = SpiderApi.lines(detail);
							synchronized (collected) {
								for (SpiderApi.Line l : sl) {
									Line line = new Line();
									line.siteKey = ref.siteKey;
									line.siteName = ref.siteName;
									line.flag = l.getFlag();
									line.items = l.getItems();
									if (!line.items.isEmpty()) {
										collected.add(line);
									}
								}
								if (firstDetail[0] == null && detail != null && detail.length() > 0) {
									firstDetail[0] = detail;
								}
							}
						} catch (Exception e) {
						}
					}
					latch.countDown();
				}
			}, "vod-detail-" + i).start();
		}
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					latch.await(DETAIL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
				} catch (Exception e) {
				}
				final ArrayList<Line> fLines = new ArrayList<Line>(collected);
				final JSONObject fDetail = firstDetail[0];
				mUi.post(new Runnable() {
					@Override
					public void run() {
						if (fLines.isEmpty()) {
							Toast.makeText(NetVodActivity.this, "该影片暂无可用线路", Toast.LENGTH_SHORT).show();
							return;
						}
						mLines = fLines;
						mLineIdx = 0;
						buildLineRow();
						buildEpisodeGrid();
						applyDetail(fDetail);
						mLoading.setText("加载中…");
					}
				});
			}
		}, "vod-detail-wait").start();
	}

	private Spider spiderOf(String siteKey) {
		if (siteKey == null) {
			return null;
		}
		for (int i = 0; i < mSites.size(); i++) {
			if (siteKey.equals(mSites.get(i).key)) {
				return (i < mSpiders.size()) ? mSpiders.get(i) : null;
			}
		}
		return null;
	}

	private void applyDetail(JSONObject d) {
		if (d == null) {
			return;
		}
		mVodName = d.optString("vod_name", mVodName);
		mDetailName.setText(mVodName);
		mDetailDirector.setText(label("导演：", d.optString("vod_director")));
		mDetailActors.setText(label("主演：", d.optString("vod_actor")));
		mDetailArea.setText(label("地区：", d.optString("vod_area")));
		mDetailYear.setText(label("年代：", d.optString("vod_year")));
		mDetailType.setText(label("类型：", d.optString("vod_class")));
		mDetailRemarks.setText(label("备注：", d.optString("vod_remarks")));
		String intro = d.optString("vod_content");
		if (intro == null || intro.length() == 0) {
			intro = d.optString("vod_blurb");
		}
		if (intro != null) {
			mDetailIntro.setText(intro.replaceAll("<[^>]+>", "").trim());
		}
		String pic = d.optString("vod_pic");
		if (pic != null && pic.length() > 0) {
			mVodPic = pic;
			loadPic(pic, mDetailPic);
		}
	}

	private String label(String prefix, String value) {
		if (value == null || value.length() == 0) {
			return "";
		}
		return prefix + value;
	}

	private void buildLineRow() {
		mDetailSources.removeAllViews();
		for (int i = 0; i < mLines.size(); i++) {
			final int idx = i;
			Line l = mLines.get(i);
			RadioButton rb = new RadioButton(this);
			rb.setText(l.siteName + " · " + l.flag + "(" + l.items.size() + ")");
			rb.setTextSize(12);
			rb.setButtonDrawable(null);
			rb.setTextColor(i == mLineIdx ? 0xFF12151C : 0xFFE6E9EE);
			rb.setBackgroundColor(i == mLineIdx ? 0xFF4FC3F7 : 0xFF2A313D);
			rb.setPadding(16, 6, 16, 6);
			rb.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (idx != mLineIdx) {
						mLineIdx = idx;
						buildLineRow();
						buildEpisodeGrid();
					}
				}
			});
			RadioGroup.LayoutParams lp = new RadioGroup.LayoutParams(
					RadioGroup.LayoutParams.WRAP_CONTENT, RadioGroup.LayoutParams.WRAP_CONTENT);
			lp.rightMargin = 8;
			rb.setLayoutParams(lp);
			mDetailSources.addView(rb, mDetailSources.getChildCount());
		}
	}

	private void buildEpisodeGrid() {
		EpisodeAdapter a = new EpisodeAdapter();
		mDetailEpisodes.setAdapter(a);
	}

	/* ==================== 播放 ==================== */

	private void playAt(int idx) {
		if (mLines.isEmpty()) {
			Toast.makeText(this, "请先选择线路", Toast.LENGTH_SHORT).show();
			return;
		}
		if (mLineIdx < 0 || mLineIdx >= mLines.size()) {
			return;
		}
		final Line line = mLines.get(mLineIdx);
		if (idx < 0 || idx >= line.items.size()) {
			return;
		}
		final SpiderApi.Item item = line.items.get(idx);
		final ArrayList<VideoInfo> infos = new ArrayList<VideoInfo>();
		for (SpiderApi.Item it : line.items) {
			VideoInfo info = new VideoInfo();
			info.title = it.name;
			info.url = it.id;
			infos.add(info);
		}
		if (item.id != null && item.id.startsWith("http")) {
			launchPlayer(item.id, infos, idx);
			return;
		}
		// 交给所属源的 playerContent 解析
		new Thread(new Runnable() {
			@Override
			public void run() {
				Spider sp = spiderOf(line.siteKey);
				SpiderApi.PlayUrl pu = null;
				if (sp != null) {
					try {
						pu = SpiderApi.play(sp, line.flag, item.id, new ArrayList<String>());
					} catch (Exception e) {
						pu = null;
					}
				}
				final SpiderApi.PlayUrl fPu = pu;
				mUi.post(new Runnable() {
					@Override
					public void run() {
						if (fPu == null || fPu.url == null || fPu.url.length() == 0) {
							Toast.makeText(NetVodActivity.this, "解析失败，请换一条线路", Toast.LENGTH_SHORT).show();
							return;
						}
						// 解析器声明需要嗅探/网页解析时，强制走网页播放器
						boolean web = fPu.parse != 0 || VideoList.shouldUseWebPlayer(fPu.url);
						ArrayList<VideoInfo> playInfos = new ArrayList<VideoInfo>();
						for (VideoInfo v : infos) {
							VideoInfo c = new VideoInfo();
							c.title = v.title;
							c.url = v.url;
							playInfos.add(c);
						}
						playInfos.get(idx).url = fPu.url;
						startPlayer(fPu.url, playInfos, idx, web);
					}
				});
			}
		}, "vod-play").start();
	}

	private void launchPlayer(String url, ArrayList<VideoInfo> infos, int idx) {
		String playUrl = VideoList.getProxiedUrl(url);
		startPlayer(playUrl, infos, idx, VideoList.shouldUseWebPlayer(playUrl));
	}

	private void startPlayer(String url, ArrayList<VideoInfo> infos, int idx, boolean web) {
		ArrayList<VideoInfo> send = new ArrayList<VideoInfo>();
		for (VideoInfo v : infos) {
			VideoInfo c = new VideoInfo();
			c.title = v.title;
			c.url = web ? VideoList.getProxiedUrl(v.url) : v.url;
			send.add(c);
		}
		Intent it = new Intent();
		it.setClass(this, web ? WebVideoPlayerActivity.class : NetVodPlayerActivity.class);
		it.putParcelableArrayListExtra("videoinfo", send);
		it.putExtra("albumPic", mVodPic);
		it.putExtra("vodtype", mTypeName == null || mTypeName.length() == 0 ? "其它" : mTypeName);
		it.putExtra("videoId", mVodName);
		it.putExtra("vodname", mVodName);
		it.putExtra("sourceId", mLineIdx >= 0 && mLineIdx < mLines.size() ? mLines.get(mLineIdx).siteName : "");
		it.putExtra("playIndex", idx);
		it.putExtra("collectionTime", 0);
		startActivity(it);
	}

	/* ==================== 通用 ==================== */

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
				byte[] data = null;
				try {
					data = TvBoxConfig.fetchBytes(url, 10000);
				} catch (Exception e) {
					data = null;
				}
				if (data == null || data.length == 0) {
					return;
				}
				final Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
				if (bmp == null) {
					return;
				}
				mPicCache.put(url, bmp);
				mUi.post(new Runnable() {
					@Override
					public void run() {
						if (url.equals(view.getTag())) {
							view.setImageBitmap(bmp);
						}
					}
				});
			}
		}, "vod-pic").start();
	}

	private class GridAdapter extends BaseAdapter {
		@Override
		public int getCount() {
			return mFilms.size();
		}

		@Override
		public VodFilm getItem(int position) {
			return mFilms.get(position);
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
			VodFilm f = mFilms.get(position);
			((TextView) v.findViewById(R.id.net_item_name)).setText(f.title);
			((TextView) v.findViewById(R.id.net_item_remarks)).setText(f.subtitle());
			TextView lines = (TextView) v.findViewById(R.id.net_item_lines);
			if (f.lineCount() > 1) {
				lines.setVisibility(View.VISIBLE);
				lines.setText(f.lineCount() + " 线路");
			} else {
				lines.setVisibility(View.GONE);
			}
			loadPic(f.pic, (ImageView) v.findViewById(R.id.net_item_pic));
			return v;
		}
	}

	private class EpisodeAdapter extends BaseAdapter {
		@Override
		public int getCount() {
			if (mLineIdx < 0 || mLineIdx >= mLines.size()) {
				return 0;
			}
			return mLines.get(mLineIdx).items.size();
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
			TextView tv = (TextView) convertView;
			if (tv == null) {
				tv = new TextView(NetVodActivity.this);
				tv.setPadding(16, 10, 16, 10);
				tv.setTextColor(0xFFFFFFFF);
				tv.setTextSize(13);
				tv.setGravity(Gravity.CENTER);
				tv.setBackgroundColor(0xFF2A313D);
				// GridView.LayoutParams 不支持 margin，用 padding 留出间距
				GridView.LayoutParams lp = new GridView.LayoutParams(
						GridView.LayoutParams.WRAP_CONTENT, GridView.LayoutParams.WRAP_CONTENT);
				tv.setLayoutParams(lp);
			}
			if (mLineIdx >= 0 && mLineIdx < mLines.size()) {
				tv.setText(mLines.get(mLineIdx).items.get(position).name);
			}
			return tv;
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

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mListSeq++;
		mCatSeq++;
	}
}
