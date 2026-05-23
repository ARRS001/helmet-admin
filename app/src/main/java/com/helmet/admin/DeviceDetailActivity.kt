package com.helmet.admin

import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class DeviceDetailActivity : AppCompatActivity() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var deviceId: String
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deviceId = intent.getStringExtra("deviceId") ?: ""
        val name = intent.getStringExtra("deviceName") ?: deviceId

        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16) }
        val sv = ScrollView(this).apply { addView(root) }
        setContentView(sv)

        scope.launch { loadDetail() }
    }

    private suspend fun loadDetail() {
        val r = ApiService.deviceDetail(deviceId)
        if (r.code != 0) { finish(); return }
        val d = r.data ?: return

        root.addView(section("基本信息"))
        root.addView(row("设备ID", d["deviceId"] as? String ?: "-"))
        root.addView(row("人员", d["personnelName"] as? String ?: "-"))
        root.addView(row("手机", d["phone"] as? String ?: "-"))
        root.addView(row("类型", d["deviceType"] as? String ?: "-"))
        root.addView(row("分组", d["groupName"] as? String ?: "未分组"))
        root.addView(row("在线", if ((d["online"] as? Number)?.toInt() == 1) "是" else "否"))
        root.addView(row("推流", if ((d["streaming"] as? Number)?.toInt() == 1) "是" else "否"))

        @Suppress("UNCHECKED_CAST")
        val assigned = d["assignedUser"] as? Map<String, Any?>
        if (assigned != null) {
            root.addView(section("绑定账户"))
            root.addView(row("用户名", assigned["username"] as? String ?: "-"))
            root.addView(row("角色", mapOf(0 to "超管", 1 to "管理员", 2 to "用户").getOrDefault((assigned["level"] as? Number)?.toInt(), "?")))
            val unbindBtn = Button(this).apply {
                text = "解绑"; setBackgroundColor(0xFFC62828.toInt()); setTextColor(0xFFFFFFFF.toInt())
                setOnClickListener {
                    AlertDialog.Builder(this@DeviceDetailActivity).setTitle("确认解绑").setMessage("解绑此设备？")
                        .setPositiveButton("确认") { _, _ ->
                            scope.launch {
                                val rr = ApiService.deviceUnassign(deviceId)
                                if (rr.code == 0) { Toast.makeText(this@DeviceDetailActivity, "已解绑", Toast.LENGTH_SHORT).show(); loadDetail() }
                                else Toast.makeText(this@DeviceDetailActivity, rr.msg, Toast.LENGTH_SHORT).show()
                            }
                        }.setNegativeButton("取消", null).show()
                }
            }
            root.addView(unbindBtn)
        } else {
            val bindBtn = Button(this).apply {
                text = "分配设备"; setBackgroundColor(0xFF5b9cf5.toInt()); setTextColor(0xFFFFFFFF.toInt())
                setOnClickListener { showAssignDialog() }
            }
            root.addView(bindBtn)
        }

        // 截图
        root.addView(section("截图"))
        scope.launch {
            val sr = ApiService.snapshots(deviceId)
            if (sr.code == 0) {
                @Suppress("UNCHECKED_CAST")
                val snaps = sr.data as? List<Map<String, Any?>>
                if (snaps != null && snaps.isNotEmpty()) {
                    root.addView(TextView(this@DeviceDetailActivity).apply { text = "共 ${snaps.size} 张"; textSize = 12f; setTextColor(0xFF8896A6.toInt()) })
                } else {
                    root.addView(TextView(this@DeviceDetailActivity).apply { text = "暂无截图"; textSize = 12f; setTextColor(0xFF8896A6.toInt()) })
                }
            }
        }

        // 通话记录
        root.addView(section("通话记录"))
        scope.launch {
            val cr = ApiService.callRecords(deviceId)
            if (cr.code == 0) {
                @Suppress("UNCHECKED_CAST")
                val calls = cr.data as? List<Map<String, Any?>>
                if (calls != null && calls.isNotEmpty()) {
                    for (c in calls.take(10)) {
                        val status = c["status"] as? String ?: ""
                        val statusLabel = mapOf("ended" to "已挂", "missed" to "未接", "answered" to "已接", "ringing" to "振铃")
                        root.addView(TextView(this@DeviceDetailActivity).apply {
                            text = "${c["callerName"]} · ${if ((c["callType"] as? String) == "video") "视频" else "语音"} · ${statusLabel[status] ?: status}"
                            textSize = 12f; setTextColor(0xFF8896A6.toInt()); setPadding(0, 2, 0, 2)
                        })
                    }
                } else {
                    root.addView(TextView(this@DeviceDetailActivity).apply { text = "暂无通话记录"; textSize = 12f; setTextColor(0xFF8896A6.toInt()) })
                }
            }
        }
    }

    private fun showAssignDialog() {
        scope.launch {
            val r = ApiService.assignableUsers()
            if (r.code != 0) return@launch
            @Suppress("UNCHECKED_CAST")
            val users = r.data as? List<Map<String, Any?>> ?: return@launch
            val names = users.map { "${it["username"]} (${it["deviceId"]?.takeIf { it != "" } ?: "未绑定"})" }.toTypedArray()
            AlertDialog.Builder(this@DeviceDetailActivity).setTitle("选择用户")
                .setItems(names) { _, i ->
                    scope.launch {
                        val rr = ApiService.deviceAssign(deviceId, users[i]["id"] as? String ?: "")
                        if (rr.code == 0) { Toast.makeText(this@DeviceDetailActivity, "分配成功", Toast.LENGTH_SHORT).show(); loadDetail() }
                        else Toast.makeText(this@DeviceDetailActivity, rr.msg, Toast.LENGTH_SHORT).show()
                    }
                }.show()
        }
    }

    private fun section(title: String): TextView =
        TextView(this).apply { text = title; textSize = 14f; setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 16, 0, 8); setTextColor(0xFF1B2A4A.toInt()) }

    private fun row(label: String, value: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, 4, 0, 4)
            addView(TextView(this@DeviceDetailActivity).apply { text = "$label:  "; textSize = 12f; setTextColor(0xFF8896A6.toInt()) })
            addView(TextView(this@DeviceDetailActivity).apply { text = value; textSize = 12f })
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
