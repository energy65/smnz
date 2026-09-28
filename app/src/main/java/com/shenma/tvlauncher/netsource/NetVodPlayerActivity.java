package com.shenma.tvlauncher.netsource;

import java.util.ArrayList;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.cyberplayer.core.BVideoView;
import com.baidu.cyberplayer.core.BVideoView.OnCompletionListener;
import com.baidu.cyberplayer.core.BVideoView.OnErrorListener;
import com.baidu.cyberplayer.core.BVideoView.OnInfoListener;
import com.baidu.cyberplayer.core.BVideoView.OnPreparedListener;
import com.shenma.tvlauncher.R;
import com.shenma.tvlauncher.utils.Constant;
import com.shenma.tvlauncher.vod.domain.VideoInfo;

/**
 * 网络点播播放器：BVideoView（百度CyberPlayer，支持 m3u8/MP4/FLV/TS 直链）
 * 与旧 VideoPlayerActivity 解耦，不经旧解析服务器，直接播放传入的 videoinfo 地址
 */
public class NetVodPlayerActivity extends Activity implements
		View.OnClickListener, OnPreparedListener, OnCompletionListener,
		OnErrorListener, OnInfoListener {

	private static final int MSG_TICK = 100;

	private BVideoView mVV;
	private TextView mTitle, mTime;
	private Button mToggle, mPrev, mNext;
	private SeekBar mSeek;
	private ProgressBar mLoading;

	private ArrayList<VideoInfo> mEpisodes;
	private int mIndex = 0;
	private String mVodName = "";
	private String mVodType = "";
	private volatile boolean mSeeking = false;
	private volatile boolean mPrepared = false;
	private int mPendingSeek = 0;

	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_TICK:
				updateProgress();
				sendEmptyMessageDelayed(MSG_TICK, 500);
				break;
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.net_vod_player);

		mVV = (BVideoView) findViewById(R.id.net_vv);
		mTitle = (TextView) findViewById(R.id.net_player_title);
		mTime = (TextView) findViewById(R.id.net_player_time);
		mToggle = (Button) findViewById(R.id.net_player_toggle);
		mPrev = (Button) findViewById(R.id.net_player_prev);
		mNext = (Button) findViewById(R.id.net_player_next);
		mSeek = (SeekBar) findViewById(R.id.net_player_seek);
		mLoading = (ProgressBar) findViewById(R.id.net_player_loading);

		mToggle.setOnClickListener(this);
		mPrev.setOnClickListener(this);
		mNext.setOnClickListener(this);
		mSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				mSeeking = true;
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				mSeeking = false;
				if (mPrepared) {
					mVV.seekTo(seekBar.getProgress());
				} else {
					mPendingSeek = seekBar.getProgress();
				}
			}
		});

		BVideoView.setAKSK(Constant.AK, Constant.SK);
		mVV.setDecodeMode(BVideoView.DECODE_HW);
		mVV.setOnPreparedListener(this);
		mVV.setOnCompletionListener(this);
		mVV.setOnErrorListener(this);
		mVV.setOnInfoListener(this);

		ArrayList<VideoInfo> infos = getIntent().getParcelableArrayListExtra("videoinfo");
		mEpisodes = infos == null ? new ArrayList<VideoInfo>() : infos;
		mIndex = getIntent().getIntExtra("playIndex", 0);
		mVodName = getIntent().getStringExtra("vodname");
		mVodType = getIntent().getStringExtra("vodtype");

		if (mEpisodes.isEmpty()) {
			Toast.makeText(this, "没有可播放的地址", Toast.LENGTH_SHORT).show();
			finish();
			return;
		}
		if (mIndex < 0 || mIndex >= mEpisodes.size()) {
			mIndex = 0;
		}
		playAt(mIndex);
		mHandler.sendEmptyMessage(MSG_TICK);
	}

	private void playAt(int index) {
		if (index < 0 || index >= mEpisodes.size()) {
			return;
		}
		mIndex = index;
		mPrepared = false;
		mPendingSeek = 0;
		mLoading.setVisibility(View.VISIBLE);
		VideoInfo info = mEpisodes.get(index);
		StringBuilder title = new StringBuilder();
		if (mVodName != null && mVodName.length() > 0) {
			title.append(mVodName).append("  ");
		}
		title.append(info.title == null ? "" : info.title);
		if (mEpisodes.size() > 1) {
			title.append("  (").append(index + 1).append("/").append(mEpisodes.size()).append(")");
		}
		mTitle.setText(title.toString());
		mVV.stopPlayback();
		mVV.setVideoPath(info.url);
		mVV.start();
	}

	private void updateProgress() {
		if (!mPrepared || mVV == null) {
			return;
		}
		int curr = mVV.getCurrentPosition();
		int total = mVV.getDuration();
		if (total <= 0) {
			return;
		}
		if (!mSeeking) {
			mSeek.setMax(total);
			mSeek.setProgress(curr);
		}
		mTime.setText(fmt(curr) + "/" + fmt(total));
	}

	private static String fmt(int ms) {
		int s = ms / 1000;
		return String.format("%02d:%02d", (s / 60) % 60, s % 60);
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		if (id == R.id.net_player_toggle) {
			if (mVV.isPlaying()) {
				mVV.pause();
				mToggle.setText("播放");
			} else {
				mVV.resume();
				mToggle.setText("暂停");
			}
		} else if (id == R.id.net_player_prev) {
			if (mIndex > 0) {
				playAt(mIndex - 1);
			} else {
				Toast.makeText(this, "已经是第一集", Toast.LENGTH_SHORT).show();
			}
		} else if (id == R.id.net_player_next) {
			if (mIndex < mEpisodes.size() - 1) {
				playAt(mIndex + 1);
			} else {
				Toast.makeText(this, "已经是最后一集", Toast.LENGTH_SHORT).show();
			}
		}
	}

	@Override
	public void onPrepared() {
		mPrepared = true;
		mLoading.setVisibility(View.GONE);
		if (mPendingSeek > 0) {
			mVV.seekTo(mPendingSeek);
			mPendingSeek = 0;
		}
	}

	@Override
	public void onCompletion() {
		if (mIndex < mEpisodes.size() - 1) {
			playAt(mIndex + 1);// 自动连播
		} else {
			finish();
		}
	}

	@Override
	public boolean onError(int what, int extra) {
		mLoading.setVisibility(View.GONE);
		Toast.makeText(this, "播放失败，请换线路或换源重试", Toast.LENGTH_LONG).show();
		finish();
		return true;
	}

	@Override
	public boolean onInfo(int what, int extra) {
		// 缓冲开始/结束等事件，仅用于显隐加载框
		if (what == 701) {// MEDIA_INFO_BUFFERING_START
			mLoading.setVisibility(View.VISIBLE);
		} else if (what == 702) {// MEDIA_INFO_BUFFERING_END
			mLoading.setVisibility(View.GONE);
		}
		return true;
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (mVV != null && mVV.isPlaying()) {
			mVV.pause();
			mToggle.setText("播放");
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (mVV != null && mPrepared && !mVV.isPlaying()) {
			mVV.resume();
			mToggle.setText("暂停");
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mHandler.removeMessages(MSG_TICK);
		if (mVV != null) {
			mVV.stopPlayback();
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			finish();
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}
}
