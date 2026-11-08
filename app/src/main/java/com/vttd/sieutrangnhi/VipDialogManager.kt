package com.vttd.sieutrangnhi

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class VipDialogManager(private val activity: MainActivity) {

  fun showParentalGateDialog(onPassed: () -> Unit) {
    if (activity.isFinishing || activity.isDestroyed) return
    activity.runOnUiThread {
      if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread

      val num1 = (5..9).random()
      val num2 = (6..9).random()
      val expectedAnswer = num1 * num2

      var dialogRef: AlertDialog? = null

      val container = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(52, 40, 52, 36)
        val bgDrawable = GradientDrawable().apply {
          setColor(Color.parseColor("#FFFBEB"))
          setStroke(8, Color.parseColor("#F59E0B"))
          cornerRadius = 40f
        }
        background = bgDrawable
      }

      val titleHeader = TextView(activity).apply {
        text = "👨‍👩‍👧‍👦 XÁC NHẬN PHỤ HUYNH"
        setTextColor(Color.parseColor("#991B1B"))
        textSize = 16f
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, 8)
      }
      container.addView(titleHeader)

      val subTitle = TextView(activity).apply {
        text = "Để bảo vệ trẻ em và tuân thủ quy định Google Play, vui lòng nhờ phụ huynh giải bài toán bên dưới để tiếp tục:"
        setTextColor(Color.parseColor("#78350F"))
        textSize = 12f
        gravity = Gravity.CENTER
        setLineSpacing(4f, 1f)
        setPadding(0, 0, 0, 16)
      }
      container.addView(subTitle)

      val mathBox = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(24, 16, 24, 16)
        val boxBg = GradientDrawable().apply {
          setColor(Color.WHITE)
          setStroke(4, Color.parseColor("#F59E0B"))
          cornerRadius = 24f
        }
        background = boxBg
        layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, 16) }
      }

      val mathQuestionText = TextView(activity).apply {
        text = "$num1 × $num2 = "
        setTextColor(Color.parseColor("#B91C1C"))
        textSize = 22f
        setTypeface(null, Typeface.BOLD)
      }

      val inputAnswer = EditText(activity).apply {
        hint = "?"
        inputType = InputType.TYPE_CLASS_NUMBER
        setSingleLine(true)
        setTextColor(Color.parseColor("#1E293B"))
        textSize = 22f
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        background = null
        layoutParams = LinearLayout.LayoutParams(140, LinearLayout.LayoutParams.WRAP_CONTENT)
      }

      mathBox.addView(mathQuestionText)
      mathBox.addView(inputAnswer)
      container.addView(mathBox)

      val btnSubmit = Button(activity).apply {
        text = "Xác nhận"
        setTextColor(Color.WHITE)
        textSize = 14f
        setTypeface(null, Typeface.BOLD)
        val btnBg = GradientDrawable().apply {
          setColor(Color.parseColor("#059669"))
          cornerRadius = 24f
        }
        background = btnBg
        setPadding(0, 18, 0, 18)
        layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 8, 0, 8) }

        setOnClickListener {
          val userAns = inputAnswer.text.toString().trim().toIntOrNull()
          if (userAns == expectedAnswer) {
            dialogRef?.dismiss()
            onPassed()
          } else {
            Toast.makeText(activity, "Kết quả chưa đúng, vui lòng thử lại!", Toast.LENGTH_SHORT).show()
          }
        }
      }
      container.addView(btnSubmit)

      val btnCancel = TextView(activity).apply {
        text = "Hủy"
        setTextColor(Color.parseColor("#DC2626"))
        textSize = 13f
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(0, 12, 0, 4)
        isClickable = true
        isFocusable = true
        setOnClickListener {
          dialogRef?.dismiss()
        }
      }
      container.addView(btnCancel)

      dialogRef = AlertDialog.Builder(activity)
        .setView(container)
        .create()

      dialogRef?.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
      }
      dialogRef?.show()
    }
  }

  fun showVipRegistrationDialog(productId: String, defaultName: String = "") {
    if (activity.isFinishing || activity.isDestroyed) return
    showParentalGateDialog {
      activity.runOnUiThread {
        if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
        val isVip2Target = productId == BillingManager.PRODUCT_VIP_2
        val vipTitleName = if (isVip2Target) "Gói VIP 2 (Trọn Đời - 99.000đ)" else "Gói VIP 1 (Mùa Thi - 49.000đ)"
        val deviceId = activity.vipPrefs.getDeviceId()
        val savedEmail = activity.vipPrefs.getUserEmail()
        val savedName = if (defaultName.isNotBlank()) defaultName else activity.vipPrefs.getUserName()

        val container = LinearLayout(activity).apply {
          orientation = LinearLayout.VERTICAL
          setPadding(52, 40, 52, 32)
          val bgDrawable = GradientDrawable().apply {
            setColor(Color.parseColor("#FFFBEB"))
            setStroke(8, Color.parseColor("#F59E0B"))
            cornerRadius = 40f
          }
          background = bgDrawable
        }

        val titleHeader = TextView(activity).apply {
          text = "👑 KÍCH HOẠT TÀI KHOẢN VIP"
          setTextColor(Color.parseColor("#991B1B"))
          textSize = 15f
          setTypeface(null, Typeface.BOLD)
          gravity = Gravity.CENTER
          setPadding(0, 0, 0, 8)
        }
        container.addView(titleHeader)

        val subTitle = TextView(activity).apply {
          text = vipTitleName
          setTextColor(Color.parseColor("#D97706"))
          textSize = 12f
          setTypeface(null, Typeface.BOLD)
          gravity = Gravity.CENTER
          setPadding(0, 0, 0, 20)
        }
        container.addView(subTitle)

        val rowAccount = LinearLayout(activity).apply {
          orientation = LinearLayout.HORIZONTAL
          gravity = Gravity.CENTER_VERTICAL
          setPadding(24, 14, 24, 14)
          val rowBg = GradientDrawable().apply {
            setColor(Color.WHITE)
            setStroke(3, Color.parseColor("#FDE68A"))
            cornerRadius = 20f
          }
          background = rowBg
          layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
          ).apply { setMargins(0, 0, 0, 14) }
        }

        val lblAccount = TextView(activity).apply {
          text = "Tài khoản :"
          setTextColor(Color.parseColor("#78350F"))
          textSize = 13f
          setTypeface(null, Typeface.BOLD)
          setPadding(0, 0, 12, 0)
        }

        val inputAccount = EditText(activity).apply {
          hint = "Nhập tên tài khoản..."
          setText(if (savedName.isNotBlank() && savedName != "Siêu Trạng Nhí") savedName else "Tài khoản VIP")
          setSingleLine(true)
          setTextColor(Color.parseColor("#1E293B"))
          textSize = 13f
          setTypeface(null, Typeface.BOLD)
          background = null
          layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        rowAccount.addView(lblAccount)
        rowAccount.addView(inputAccount)
        container.addView(rowAccount)

        val rowEmail = LinearLayout(activity).apply {
          orientation = LinearLayout.HORIZONTAL
          gravity = Gravity.CENTER_VERTICAL
          setPadding(24, 14, 24, 14)
          val rowBg = GradientDrawable().apply {
            setColor(Color.WHITE)
            setStroke(3, Color.parseColor("#FDE68A"))
            cornerRadius = 20f
          }
          background = rowBg
          layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
          ).apply { setMargins(0, 0, 0, 14) }
        }

        val lblEmail = TextView(activity).apply {
          text = "Email :"
          setTextColor(Color.parseColor("#78350F"))
          textSize = 13f
          setTypeface(null, Typeface.BOLD)
          setPadding(0, 0, 12, 0)
        }

        val inputEmail = EditText(activity).apply {
          hint = "phuhuynh@gmail.com"
          setText(savedEmail)
          inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
          setSingleLine(true)
          setTextColor(Color.parseColor("#1E293B"))
          textSize = 13f
          setTypeface(null, Typeface.BOLD)
          background = null
          layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        rowEmail.addView(lblEmail)
        rowEmail.addView(inputEmail)
        container.addView(rowEmail)

        val rowDevice = LinearLayout(activity).apply {
          orientation = LinearLayout.HORIZONTAL
          gravity = Gravity.CENTER_VERTICAL
          setPadding(24, 14, 24, 14)
          val rowBg = GradientDrawable().apply {
            setColor(Color.parseColor("#FEF3C7"))
            setStroke(3, Color.parseColor("#F59E0B"))
            cornerRadius = 20f
          }
          background = rowBg
          layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
          )
        }

        val lblDevice = TextView(activity).apply {
          text = "ID :"
          setTextColor(Color.parseColor("#78350F"))
          textSize = 13f
          setTypeface(null, Typeface.BOLD)
          setPadding(0, 0, 12, 0)
        }

        val textDevice = TextView(activity).apply {
          text = deviceId
          setTextColor(Color.parseColor("#991B1B"))
          textSize = 13f
          setTypeface(null, Typeface.BOLD)
          layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        rowDevice.addView(lblDevice)
        rowDevice.addView(textDevice)
        container.addView(rowDevice)

        var dialogRef: AlertDialog? = null

        val btnPay = Button(activity).apply {
          text = "Thanh toán Google Play"
          setTextColor(Color.WHITE)
          textSize = 13f
          setTypeface(null, Typeface.BOLD)
          val btnBg = GradientDrawable().apply {
            setColor(Color.parseColor("#059669"))
            cornerRadius = 24f
          }
          background = btnBg
          setPadding(0, 20, 0, 20)
          layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
          ).apply { setMargins(0, 20, 0, 12) }

          setOnClickListener {
            val inputName = inputAccount.text.toString().trim()
            val inputEmailStr = inputEmail.text.toString().trim()

            if (inputName.isNotBlank()) activity.vipPrefs.setUserName(inputName)
            if (inputEmailStr.isNotBlank()) activity.vipPrefs.setUserEmail(inputEmailStr)

            dialogRef?.dismiss()
            activity.billingManager.launchBillingFlow(activity, productId, inputEmailStr, inputName)
          }
        }
        container.addView(btnPay)

        val btnRestore = Button(activity).apply {
          text = "Khôi phục VIP"
          setTextColor(Color.WHITE)
          textSize = 13f
          setTypeface(null, Typeface.BOLD)
          val btnBg = GradientDrawable().apply {
            setColor(Color.parseColor("#D97706"))
            cornerRadius = 24f
          }
          background = btnBg
          setPadding(0, 20, 0, 20)
          layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
          ).apply { setMargins(0, 0, 0, 12) }

          setOnClickListener {
            val inputName = inputAccount.text.toString().trim()
            val inputEmailStr = inputEmail.text.toString().trim()
            dialogRef?.dismiss()
            activity.billingManager.scanAndRestoreVip(inputName, inputEmailStr) { success, vipLevel, message ->
              activity.runOnUiThread {
                if (success) {
                  activity.updateAdMobForVipStatus()
                  val activeName = inputName.ifBlank { activity.vipPrefs.getUserName().ifBlank { "Tài khoản VIP" } }
                  activity.vipPrefs.setUserName(activeName)
                  if (inputEmailStr.isNotBlank()) activity.vipPrefs.setUserEmail(inputEmailStr)

                  activity.webView?.evaluateJavascript(
                    "if (window.onVipStatusChanged) { window.onVipStatusChanged($vipLevel, '$activeName'); }",
                    null
                  )
                  Toast.makeText(activity, "🎉 $message", Toast.LENGTH_LONG).show()
                } else {
                  Toast.makeText(activity, "🔍 $message", Toast.LENGTH_LONG).show()
                }
              }
            }
          }
        }
        container.addView(btnRestore)

        val btnCancel = TextView(activity).apply {
          text = "Hủy"
          setTextColor(Color.parseColor("#DC2626"))
          textSize = 13f
          setTypeface(null, Typeface.BOLD)
          gravity = Gravity.CENTER
          setPadding(0, 12, 0, 4)
          isClickable = true
          isFocusable = true
          setOnClickListener {
            dialogRef?.dismiss()
          }
        }
        container.addView(btnCancel)

        dialogRef = AlertDialog.Builder(activity)
          .setView(container)
          .create()

        dialogRef?.window?.apply {
          setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
          clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        dialogRef?.show()
      }
    }
  }

  fun showVipSuccessDialog(productId: String, vipLevel: Int) {
    if (activity.isFinishing || activity.isDestroyed) return
    activity.runOnUiThread {
      if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
      val vipName = if (vipLevel >= 2) "VIP 2 (Trọn Đời)" else "VIP 1 (Mùa Thi)"
      val email = activity.vipPrefs.getUserEmail().ifBlank { "Đã ghi nhận" }
      val name = activity.vipPrefs.getUserName().ifBlank { "Học sinh" }
      val deviceId = activity.vipPrefs.getDeviceId()

      var dialogRef: AlertDialog? = null

      val container = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(48, 44, 48, 44)
        val bgDrawable = GradientDrawable().apply {
          setColor(Color.parseColor("#FFFDF7"))
          cornerRadius = 48f
          setStroke(6, Color.parseColor("#F59E0B"))
        }
        background = bgDrawable
        elevation = 20f
      }

      val headerIcon = TextView(activity).apply {
        text = "👑"
        textSize = 40f
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, 8) }
      }
      container.addView(headerIcon)

      val txtTitle = TextView(activity).apply {
        text = "KÍCH HOẠT VIP THÀNH CÔNG 🎉"
        setTextColor(Color.parseColor("#92400E"))
        textSize = 17f
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, 10) }
      }
      container.addView(txtTitle)

      val txtGreeting = TextView(activity).apply {
        text = "Chúc mừng $name đã nâng cấp thành công gói $vipName!"
        setTextColor(Color.parseColor("#1E293B"))
        textSize = 14f
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        setLineSpacing(6f, 1f)
        layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, 18) }
      }
      container.addView(txtGreeting)

      val infoBox = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(32, 24, 32, 24)
        val boxBg = GradientDrawable().apply {
          setColor(Color.parseColor("#FFFBEB"))
          cornerRadius = 28f
          setStroke(3, Color.parseColor("#FDE68A"))
        }
        background = boxBg
        layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, 16) }
      }

      val rows = listOf(
        "👤 Tài khoản:" to name,
        "✉️ Email xác nhận:" to email,
        "📱 Mã thiết bị:" to deviceId,
        "⭐ Trạng thái:" to "Mở khóa toàn bộ 18 vòng & VIP"
      )

      rows.forEach { (label, value) ->
        val row = LinearLayout(activity).apply {
          orientation = LinearLayout.HORIZONTAL
          layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
          ).apply { setMargins(0, 4, 0, 4) }
        }
        val lbl = TextView(activity).apply {
          text = label
          setTextColor(Color.parseColor("#78350F"))
          textSize = 12f
          setTypeface(null, Typeface.BOLD)
          setPadding(0, 0, 8, 0)
        }
        val valTxt = TextView(activity).apply {
          text = value
          setTextColor(if (label.contains("Mã thiết bị")) Color.parseColor("#B91C1C") else Color.parseColor("#0F172A"))
          textSize = 12f
          setTypeface(null, Typeface.BOLD)
          layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(lbl)
        row.addView(valTxt)
        infoBox.addView(row)
      }
      container.addView(infoBox)

      val txtNotice = TextView(activity).apply {
        text = "Thông tin kích hoạt đã được đồng bộ tự động."
        setTextColor(Color.parseColor("#B45309"))
        textSize = 11f
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, 18) }
      }
      container.addView(txtNotice)

      val btnSubmit = Button(activity).apply {
        text = "Bắt đầu học ngay 🚀"
        setTextColor(Color.WHITE)
        textSize = 14f
        setTypeface(null, Typeface.BOLD)
        val btnBg = GradientDrawable().apply {
          orientation = GradientDrawable.Orientation.TOP_BOTTOM
          colors = intArrayOf(Color.parseColor("#10B981"), Color.parseColor("#059669"))
          cornerRadius = 28f
        }
        background = btnBg
        setPadding(0, 22, 0, 22)
        layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        )
        setOnClickListener {
          dialogRef?.dismiss()
        }
      }
      container.addView(btnSubmit)

      dialogRef = AlertDialog.Builder(activity)
        .setView(container)
        .setCancelable(false)
        .create()

      dialogRef?.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
      }
      dialogRef?.show()
    }
  }
}
