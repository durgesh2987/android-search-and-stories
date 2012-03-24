package com.duckduckgo.mobile.android;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.List;

import com.duckduckgo.mobile.android.adapters.AutoCompleteResultsAdapter;
import com.duckduckgo.mobile.android.adapters.MainFeedAdapter;
import com.duckduckgo.mobile.android.objects.FeedObject;
import com.duckduckgo.mobile.android.tasks.MainFeedTask;
import com.duckduckgo.mobile.android.tasks.MainFeedTask.FeedListener;
import com.duckduckgo.mobile.android.views.MainFeedListView;
import com.duckduckgo.mobile.android.views.MainFeedListView.OnMainFeedItemSelectedListener;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class DuckDuckGo extends Activity implements OnEditorActionListener, FeedListener, OnClickListener, OnItemClickListener {

	protected final String TAG = "DuckDuckGo";
	
	private AutoCompleteTextView searchField = null;
	private ProgressBar feedProgressBar = null;
	private MainFeedListView feedView = null;
	private MainFeedAdapter feedAdapter = null;
	private MainFeedTask mainFeedTask = null;
	private WebView mainWebView = null;
	private ImageButton homeSettingsButton = null;
	
	private boolean hasUpdatedFeed = false;
	private boolean webviewShowing = false;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        homeSettingsButton = (ImageButton) findViewById(R.id.settingsButton);
        homeSettingsButton.setOnClickListener(this);
        
        searchField = (AutoCompleteTextView) findViewById(R.id.searchEditText);
        searchField.setAdapter(new AutoCompleteResultsAdapter(this));
        searchField.setOnEditorActionListener(this);
        searchField.setOnItemClickListener(this);
        feedAdapter = new MainFeedAdapter(this);
        feedView = (MainFeedListView) findViewById(R.id.mainFeedItems);
        feedView.setAdapter(feedAdapter);
        feedView.setOnMainFeedItemSelectedListener(new OnMainFeedItemSelectedListener() {
			public void onMainFeedItemSelected(FeedObject feedObject) {
				String url = feedObject.getUrl();
				if (url != null) {
					searchOrGoToUrl(url);
				}
			}
        });
        
        // NOTE: After loading url multiple times on the device, it may crash
        // Related to android bug report 21266 - Watch this ticket for possible resolutions
        // http://code.google.com/p/android/issues/detail?id=21266
        // Possibly also related to CSS Transforms (bug 21305)
        // http://code.google.com/p/android/issues/detail?id=21305
        mainWebView = (WebView) findViewById(R.id.mainWebView);
        mainWebView.getSettings().setJavaScriptEnabled(true);
        mainWebView.setWebViewClient(new WebViewClient() {
        	@Override
        	public boolean shouldOverrideUrlLoading(WebView view, String url) {
        		view.loadUrl(url);
        		return true;
        	}
        	
        	@Override
        	public void onPageFinished (WebView view, String url) {
        		if (url.contains("duckduckgo.com")) {
        			URL fullURL = null;
        			try {
						fullURL = new URL(url);
					} catch (MalformedURLException e) {
						e.printStackTrace();
					}

        			if (fullURL != null) {
        				//Okay, it's a valid url, which we already knew...
        				String query = fullURL.getQuery();
        				if (query != null) {
        					//Get the actual query string now...
        					int index = query.indexOf("q=");
        					if (index != -1) {
            					String text = query.substring(query.indexOf("q=") + 2);
            					if (text.contains("&")) {
            						text = text.substring(0, text.indexOf("&"));
            					}
            					String realText = URLDecoder.decode(text);
            					setSearchBarText(realText);
        					} else {
        						setSearchBarText(url);
        					}
        				} else {
        					setSearchBarText(url);
        				}
        			} else {
        				//Just in case...
        				setSearchBarText(url);
        			}
        		} else {
        			//This isn't duckduck go...
        			setSearchBarText(url);
        		}
        	}
        });
        
        feedProgressBar = (ProgressBar) findViewById(R.id.feedLoadingProgress);
    }
	
	public void setSearchBarText(String text) {
		searchField.setFocusable(false);
		searchField.setFocusableInTouchMode(false);
		searchField.setText(text);            
		searchField.setFocusable(true);
		searchField.setFocusableInTouchMode(true);
	}
	
	@Override
	public void onResume() {
		super.onResume();
		if (!hasUpdatedFeed) {
			mainFeedTask = new MainFeedTask(this);
			mainFeedTask.execute();
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		if (mainFeedTask != null) {
			mainFeedTask.cancel(false);
			mainFeedTask = null;
		}
	}
	
	@Override
	public void onBackPressed() {
		if (webviewShowing) {
			if (mainWebView.canGoBack()) {
				mainWebView.goBack();
			} else {
				feedView.setVisibility(View.VISIBLE);
				mainWebView.setVisibility(View.GONE);
				mainWebView.clearView();
				homeSettingsButton.setImageResource(R.drawable.settings_button);
				webviewShowing = false;
				searchField.setText("");
			}
		} else {
			super.onBackPressed();
		}
	}
	
	public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		if (v == searchField) {
			InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
			searchField.dismissDropDown();

			String text = searchField.getText().toString();
			text.trim();
			
			searchOrGoToUrl(text);
		}
		
		return false;
	}
	
	public void searchOrGoToUrl(String text) {
		if (text.length() > 0) {
			URL searchAsUrl = null;
			String modifiedText = null;

			try {
				searchAsUrl = new URL(text);
				searchAsUrl.toURI();
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (URISyntaxException e) {
				e.printStackTrace();
				searchAsUrl = null;
			}
			
			if (searchAsUrl == null) {
				modifiedText = "http://" + text;
				try {
					searchAsUrl = new URL(modifiedText);
					searchAsUrl.toURI();
				} catch (MalformedURLException e) {
					e.printStackTrace();
				} catch (URISyntaxException e) {
					e.printStackTrace();
					searchAsUrl = null;
				}
			}

			//We use the . check to determine if this is a single word or not... 
			//if it doesn't contain a . plus domain (2 more characters) it won't be a URL, even if it's valid, like http://test
			if (searchAsUrl != null) {
				if (modifiedText != null) {
					//Show the modified url text
					if (modifiedText.contains(".") && modifiedText.length() > (modifiedText.indexOf(".") + 2)) {
						showWebUrl(modifiedText);
					} else {
						searchWebTerm(text);
					}
				} else {
					if (text.contains(".") && text.length() > (text.indexOf(".") + 2)) {
						//Show the url text
						showWebUrl(text);
					} else {
						searchWebTerm(text);
					}
				}
			} else {
				searchWebTerm(text);
			}
		}
	}
	
	public void searchWebTerm(String term) {
		if (!webviewShowing) {
			feedView.setVisibility(View.GONE);
			mainWebView.setVisibility(View.VISIBLE);
			homeSettingsButton.setImageResource(R.drawable.home_button);
			webviewShowing = true;
		}
		
		mainWebView.loadUrl(DDGConstants.SEARCH_URL + URLEncoder.encode(term));
	}
	
	public void showWebUrl(String url) {
		if (!webviewShowing) {
			feedView.setVisibility(View.GONE);
			mainWebView.setVisibility(View.VISIBLE);
			homeSettingsButton.setImageResource(R.drawable.home_button);
			webviewShowing = true;
		}
		
		mainWebView.loadUrl(url);
	}

	public void onFeedRetrieved(List<FeedObject> feed) {
		feedProgressBar.setVisibility(View.GONE);
		feedAdapter.setList(feed);
		feedAdapter.notifyDataSetChanged();
		hasUpdatedFeed = true;
	}
	
	public void onFeedRetrievalFailed() {
		//If the mainFeedTask is null, we are currently paused
		//Otherwise, we can try again
		if (mainFeedTask != null) {
			mainFeedTask = new MainFeedTask(this);
			mainFeedTask.execute();
		}
	}

	public void onClick(View v) {
		if (v.equals(homeSettingsButton)) {
			//This is our button
			if (webviewShowing) {
				//We are going home!
				feedView.setVisibility(View.VISIBLE);
				mainWebView.setVisibility(View.GONE);
				mainWebView.clearHistory();
				mainWebView.clearView();
				homeSettingsButton.setImageResource(R.drawable.settings_button);
				searchField.setText("");
				webviewShowing = false;
			}
		}
	}

	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		//Hide the keyboard and perform a search
		InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
		searchField.dismissDropDown();
		
		String text = (String)parent.getAdapter().getItem(position);
		if (text != null) {
			text.trim();
			searchOrGoToUrl(text);
		}
	}
}