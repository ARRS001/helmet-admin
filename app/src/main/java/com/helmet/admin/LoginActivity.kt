package com.helmet.admin

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import kotlinx.coroutines.*

class LoginActivity : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvError: TextView
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvError = findViewById(R.id.tvError)

        val prefs = getSharedPreferences("admin_prefs", MODE_PRIVATE)
        etUsername.setText(prefs.getString("username", "") ?: "")

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()
            if (username.isEmpty() || password.isEmpty()) {
                tvError.text = "请输入用户名和密码"; tvError.visibility = View.VISIBLE; return@setOnClickListener
            }
            btnLogin.isEnabled = false; tvError.visibility = View.GONE
            scope.launch {
                try {
                    val r = ApiService.login(username, password)
                    if (r.code == 0) {
                        prefs.edit { putString("username", username) }
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    } else {
                        tvError.text = r.msg ?: "登录失败"; tvError.visibility = View.VISIBLE
                    }
                } catch (e: Exception) {
                    tvError.text = "网络异常: ${e.message}"; tvError.visibility = View.VISIBLE
                }
                btnLogin.isEnabled = true
            }
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
