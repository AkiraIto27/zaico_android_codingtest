package jp.co.zaico.codingtest

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class AddActivity : AppCompatActivity() {

    companion object {
        fun createIntent(context: Context) = Intent(context, AddActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add)
    }

}