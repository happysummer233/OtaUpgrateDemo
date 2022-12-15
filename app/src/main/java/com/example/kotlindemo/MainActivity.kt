package com.example.kotlindemo

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.os.*
import android.os.PowerManager.WakeLock
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.blankj.utilcode.util.FileUtils
import com.blankj.utilcode.util.ShellUtils
import com.blankj.utilcode.util.ToastUtils
import com.example.kotlindemo.UpdateParser.ParsedUpdate
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener, View.OnClickListener {

    private lateinit var tts: TextToSpeech
    var TAG = "demo"
    private lateinit var tv_sys_update: TextView
    private var dialog: ProgressDialog? = null
    private var mUpdateEngineCallback: UpdateEngineCallback = object : UpdateEngineCallback() {
        override fun onStatusUpdate(status: Int, percent: Float) {
            if (status == UpdateEngine.UpdateStatusConstants.DOWNLOADING) {
//            DecimalFormat df = new DecimalFormat("#");
//            String progress = df.format(percent * 100);
                Log.d(TAG, "update progress: " + percent + ";" + (percent * 100).toInt())
                handler.sendEmptyMessage((percent * 100).toInt())
            }
        }

        override fun onPayloadApplicationComplete(errorCode: Int) {
            if (errorCode == UpdateEngine.ErrorCodeConstants.SUCCESS) { // 回调状态
                Log.i(TAG, "UPDATE SUCCESS!")
                dialog!!.dismiss()
            }
        }
    }
    var handler: Handler = @SuppressLint("HandlerLeak")
    object : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            if (!dialog!!.isShowing) {
                dialog!!.show()
            }
            dialog!!.progress = msg.what
            Log.i(TAG, "setProgress===" + msg.what)
        }
    }
    var mWakelock: WakeLock? = null

    @SuppressLint("InvalidWakeLockTag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        var btn_read = findViewById<Button>(R.id.btn_read)
        btn_read.setOnClickListener(this)
        var btn_use_ifly = findViewById<Button>(R.id.btn_use_ifly)
        btn_use_ifly.setOnClickListener(this)
        var btn_use_mui = findViewById<Button>(R.id.btn_use_mui)
        btn_use_mui.setOnClickListener(this)
        var btn_sys_update = findViewById<Button>(R.id.btn_sys_update)
        btn_sys_update.setOnClickListener(this)
        tv_sys_update = findViewById<TextView>(R.id.tv_sys_update)
        tv_sys_update.setOnClickListener(this)
        val pm = this.getSystemService(POWER_SERVICE) as PowerManager
        mWakelock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "OTA Wakelock")
        //Removing Auto-focus of keyboard
        //Removing Auto-focus of keyboard
        val view = this.currentFocus
        if (view != null) { //Removing on screen keyboard if still active
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
        Log.i("HHH", "start fun")
//        GlobalScope.launch {
//            val arg1 = async { sunpendF1() }
//
//            var arg2 = async { sunpendF2() }
//
//            arg1.getCompleted()
//            Log.i("HHH","finish : "+arg1.await()+" "+arg2.await()+" "+(arg1.await()+arg2.await()))
//        }

        tts = TextToSpeech(this, this)
        tts.setEngineByPackageName("com.xiaomi.mibrain.speech")

    }



    private suspend fun sunpendF1(): Int {
        delay(2000)
        Log.i("HHH", "fun 1")
        return 2;
    }

    private suspend fun sunpendF2(): Int {
        delay(1000)
        Log.i("HHH", "fun 2")
        return 4
    }

    override fun onInit(p0: Int) {
        if (p0 == TextToSpeech.SUCCESS) {
//            tts.setLanguage(Locale.ENGLISH)
//            tts.setEngineByPackageName("com.iflytek.speechsuite")

        }
    }

    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.N)
    override fun onClick(p0: View?) {
        when (p0?.id) {
            R.id.btn_read -> {
                GlobalScope.launch {
                    delay(2000)
                    tts.speak("飞行器已连接", TextToSpeech.QUEUE_ADD, null, "1")
                }
//                tts.setPitch(1.0f)
                tts.speak("飞行器1已断开", TextToSpeech.QUEUE_ADD, null, "1")
            }
            R.id.btn_use_ifly -> {
                tts.setEngineByPackageName("com.iflytek.speechsuite")
            }
            R.id.btn_use_mui -> {
                tts.setEngineByPackageName("com.xiaomi.mibrain.speech")
            }
            R.id.btn_sys_update -> {
                dialog = ProgressDialog(this)
                dialog!!.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
                dialog!!.setTitle("正在下载")
                dialog!!.setMax(100)
                val dest =
                    Environment.getExternalStorageDirectory().absolutePath + "/Android/data/com.example.kotlindemo/update.zip"
                Log.d(TAG, "dest: $dest")
                val file: File = File(dest)
                if (!FileUtils.isFile(file)) {
                    ToastUtils.showShort("ota文件为空")
                    return
                }
                try {
                    var result: ParsedUpdate = UpdateParser.parse(file)!!
                    if (!result.isValid) {
                        Toast.makeText(applicationContext, "verify_failure", Toast.LENGTH_SHORT)
                            .show()

                        Log.e(TAG, "Failed verification $result")
                        return
                    }
                    mWakelock!!.acquire()
                    val mUpdateEngine = UpdateEngine()
                    Log.e(TAG, "applyPayload start ")
                    try {
                        mUpdateEngine.bind(mUpdateEngineCallback)
                        mUpdateEngine.applyPayload(
                            result.mUrl,
                            result.mOffset,
                            result.mSize,
                            result.mProps
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, String.format("For file %s", file), e)
                        return
                    }
                    Log.e(TAG, "applyPayload end ")
                    mWakelock!!.release()

                    tv_sys_update.text =
                        "update_engine_client --update --follow --payload=" + result.mUrl + " --offset=" + result.mOffset + " --size=" + result.mSize + " --headers=\"" + result.mProps[0] + "\n" + result.mProps[1] + "\n" + result.mProps[2] + "\n" + result.mProps[3] + "\n\""
//                    ThreadUtils.executeByCached(object : SimpleTask<ShellUtils.CommandResult?>() {
//                        @Throws(Throwable::class)
//                        override fun doInBackground(): ShellUtils.CommandResult {
//                            return ShellUtils.execCmd(
//                                "update_engine_client --update --follow --payload=" + result.mUrl + " --offset=" + result.mOffset + " --size=" + result.mSize + " --headers=\"" + result.mProps[0] + "\n" + result.mProps[1] + "\n" + result.mProps[2] + "\n" + result.mProps[3] + "\n\"",
//                                true
//                            )
//                        }
//
//                        override fun onSuccess(result: ShellUtils.CommandResult?) {
//                            Log.d(TAG, "onSuccess: " + result)
//                        }
//                    })

//                    Log.d(
//                        TAG,
//                        "update_engine_client --update --follow --payload=" + result.mUrl + " --offset=" + result.mOffset + " --size=" + result.mSize + " --headers=\"" + result.mProps[0] + "\n" + result.mProps[1] + "\n" + result.mProps[2] + "\n" + result.mProps[3] + "\n\""
//                    )

                } catch (e: Exception) {
                    Log.d(TAG, "Exception: $e")
                }

            }
        }
    }


}