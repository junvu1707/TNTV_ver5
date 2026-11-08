package com.vttd.sieutrangnhi

import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import org.json.JSONObject
import java.lang.ref.WeakReference

class AndroidBridge(
  activity: MainActivity,
  private val webViewProvider: () -> WebView?
) {
  private val activityRef = WeakReference(activity)
  private val activity: MainActivity? get() = activityRef.get()

  @JavascriptInterface
  fun launchBillingFlow(productId: String) {
    val act = activity ?: return
    act.showVipRegistrationDialog(productId, act.vipPrefs.getUserName())
  }

  @JavascriptInterface
  fun launchBillingFlowWithInfo(productId: String, userEmail: String, userName: String) {
    val act = activity ?: return
    act.runOnUiThread {
      act.showParentalGateDialog {
        act.billingManager.launchBillingFlow(act, productId, userEmail, userName)
      }
    }
  }

  @JavascriptInterface
  fun openVipPurchaseDialog(productId: String, defaultName: String) {
    val act = activity ?: return
    act.showVipRegistrationDialog(productId, defaultName)
  }

  @JavascriptInterface
  fun restorePurchases() {
    val act = activity ?: return
    act.runOnUiThread {
      act.billingManager.queryPurchases()
      Toast.makeText(act, "Đã kiểm tra và đồng bộ trạng thái VIP thành công!", Toast.LENGTH_SHORT).show()
    }
  }

  @JavascriptInterface
  fun scanAndRestoreVip(userName: String, userEmail: String) {
    val act = activity ?: return
    act.runOnUiThread {
      act.billingManager.scanAndRestoreVip(userName, userEmail) { success, vipLevel, message ->
        act.runOnUiThread {
          if (success) {
            act.updateAdMobForVipStatus()
            val activeName = userName.ifBlank { act.vipPrefs.getUserName().ifBlank { "Tài khoản VIP" } }
            act.vipPrefs.setUserName(activeName)
            if (userEmail.isNotBlank()) act.vipPrefs.setUserEmail(userEmail)

            act.webView?.evaluateJavascript(
              "if (window.onVipStatusChanged) { window.onVipStatusChanged($vipLevel, '$activeName'); }",
              null
            )
            Toast.makeText(act, "🎉 $message", Toast.LENGTH_LONG).show()
          } else {
            Toast.makeText(act, "🔍 $message", Toast.LENGTH_LONG).show()
          }
        }
      }
    }
  }

  @JavascriptInterface
  fun getChibiLocalPath(fileName: String): String {
    val act = activity ?: return "file:///android_asset/app_logo.png"
    return act.chibiManager.getChibiUriForWebView(fileName)
  }

  @JavascriptInterface
  fun getVipLevel(): Int {
    return activity?.vipPrefs?.getVipLevel() ?: VipPreferenceManager.VIP_LEVEL_0
  }

  @JavascriptInterface
  fun isVip(): Boolean {
    return activity?.vipPrefs?.isVip() ?: false
  }

  @JavascriptInterface
  fun isVip2(): Boolean {
    return activity?.vipPrefs?.isVip2() ?: false
  }

  @JavascriptInterface
  fun getDeviceId(): String {
    return activity?.vipPrefs?.getDeviceId() ?: ""
  }

  @JavascriptInterface
  fun getUserEmail(): String {
    return activity?.vipPrefs?.getUserEmail() ?: ""
  }

  @JavascriptInterface
  fun getUserName(): String {
    return activity?.vipPrefs?.getUserName() ?: ""
  }

  @JavascriptInterface
  fun isRoundUnlocked(roundIndex: Int): Boolean {
    return activity?.vipPrefs?.isRoundUnlocked(roundIndex) ?: (roundIndex <= 6)
  }

  @JavascriptInterface
  fun getVipInfoJson(): String {
    val act = activity ?: return "{}"
    val json = JSONObject().apply {
      put("vipLevel", act.vipPrefs.getVipLevel())
      put("isVip", act.vipPrefs.isVip())
      put("isVip1", act.vipPrefs.getVipLevel() == VipPreferenceManager.VIP_LEVEL_1)
      put("isVip2", act.vipPrefs.getVipLevel() == VipPreferenceManager.VIP_LEVEL_2)
      put("deviceId", act.vipPrefs.getDeviceId())
      put("userName", act.vipPrefs.getUserName())
      put("userEmail", act.vipPrefs.getUserEmail())
      put("lastOrderId", act.vipPrefs.getLastOrderId())
    }
    return json.toString()
  }

  @JavascriptInterface
  fun requestRewardedAd(rewardType: String, targetId: String) {
    val act = activity ?: return
    act.runOnUiThread {
      act.showRewardedAd(
        onRewarded = {
          act.runOnUiThread {
            webViewProvider()?.evaluateJavascript(
              "if (window.onRewardedAdSuccess) { window.onRewardedAdSuccess('$rewardType', '$targetId'); }",
              null
            )
          }
        },
        onFailedOrDismissed = {
          act.runOnUiThread {
            webViewProvider()?.evaluateJavascript(
              "if (window.onRewardedAdFallback) { window.onRewardedAdFallback('$rewardType', '$targetId'); }",
              null
            )
          }
        }
      )
    }
  }

  @JavascriptInterface
  fun requestRewardedAd(rewardType: String, targetId: Int) {
    requestRewardedAd(rewardType, targetId.toString())
  }

  @JavascriptInterface
  fun requestRewardedAd(roundIndex: Int) {
    requestRewardedAd("round", roundIndex.toString())
  }

  @JavascriptInterface
  fun requestInterstitialAd() {
    val act = activity ?: return
    act.runOnUiThread {
      act.showInterstitialAd {
        act.runOnUiThread {
          webViewProvider()?.evaluateJavascript(
            "if (window.onInterstitialAdClosed) { window.onInterstitialAdClosed(); }",
            null
          )
        }
      }
    }
  }

  @JavascriptInterface
  fun setBannerVisible(visible: Boolean) {
    activity?.setBannerVisibility(visible)
  }

  @JavascriptInterface
  fun generateDynamicExam(grade: Int, changOrRound: Int, seed: Int, fallbackJson: String = ""): String {
    val act = activity ?: return ""
    return act.examGenerator.generateDynamicMode2Exam(
      grade = grade,
      changOrRound = changOrRound,
      seed = seed,
      localQuestionsJsonStr = fallbackJson,
      targetCount = 30
    )
  }

  @JavascriptInterface
  fun generateDynamicMode2Exam(grade: Int, changOrRound: Int, seed: Int, fallbackJson: String = ""): String {
    return generateDynamicExam(grade, changOrRound, seed, fallbackJson)
  }

  @JavascriptInterface
  fun getGithubQuestionsJson(grade: Int, changOrRound: Int): String {
    return activity?.githubRepo?.getCachedQuestionsJson(grade, changOrRound) ?: ""
  }

  @JavascriptInterface
  fun getLocalAssetQuestionsJson(grade: Int, changOrRound: Int): String {
    return activity?.githubRepo?.getLocalAssetQuestionsJson(grade, changOrRound) ?: ""
  }

  @JavascriptInterface
  fun getDirectRawUrl(grade: Int, changOrRound: Int): String {
    return GitHubQuestionRepository.getDirectRawUrl(grade, changOrRound)
  }

  @JavascriptInterface
  fun fetchGithubQuestions(grade: Int, changOrRound: Int) {
    val act = activity ?: return
    act.githubRepo.fetchAndCacheQuestions(grade, changOrRound, onSuccess = {
      act.runOnUiThread {
        webViewProvider()?.evaluateJavascript(
          "if (window.onGithubQuestionsUpdated) { window.onGithubQuestionsUpdated($grade, $changOrRound); }",
          null
        )
      }
    })
  }

  @JavascriptInterface
  fun syncAllGithubQuestions(force: Boolean = true) {
    val act = activity ?: return
    act.githubRepo.syncRepoQuestions(forceRefresh = force) { success, message ->
      act.runOnUiThread {
        val safeMsg = message.replace("'", "\\'").replace("\n", " ")
        webViewProvider()?.evaluateJavascript(
          "if (window.onGithubSyncCompleted) { window.onGithubSyncCompleted($success, '$safeMsg'); }",
          null
        )
      }
    }
  }

  @JavascriptInterface
  fun showToast(message: String) {
    val act = activity ?: return
    act.runOnUiThread {
      Toast.makeText(act, message, Toast.LENGTH_SHORT).show()
    }
  }

  @JavascriptInterface
  fun exitApp() {
    val act = activity ?: return
    act.runOnUiThread {
      act.finish()
    }
  }

  @JavascriptInterface
  fun openBrowserUrl(url: String) {
    val act = activity ?: return
    act.runOnUiThread {
      act.showParentalGateDialog {
        try {
          val targetUri = if (url == "privacy.html" || url.endsWith("/privacy.html")) {
            Uri.parse("https://sites.google.com/view/sieutrangnhi-tiengviet/trang-ch%E1%BB%A7")
          } else if (url.startsWith("http://") || url.startsWith("https://")) {
            Uri.parse(url)
          } else {
            Uri.parse(url)
          }
          val intent = Intent(Intent.ACTION_VIEW, targetUri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
          }
          act.startActivity(intent)
        } catch (e: Exception) {
          act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/sieutrangnhi-tiengviet/trang-ch%E1%BB%A7")))
        }
      }
    }
  }
}
