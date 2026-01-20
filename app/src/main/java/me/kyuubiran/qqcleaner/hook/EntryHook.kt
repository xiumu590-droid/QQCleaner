package me.kyuubiran.qqcleaner.hook

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter        // ← 补
import android.widget.ListView
import com.github.kyuubiran.ezxhelper.utils.*
import com.github.kyuubiran.ezxhelper.utils.Log.logeIfThrow
import me.kyuubiran.qqcleaner.MainActivity
import me.kyuubiran.qqcleaner.util.hostApp
import me.kyuubiran.qqcleaner.util.hostAppName
import me.kyuubiran.qqcleaner.util.isQqOrTim
import me.kyuubiran.qqcleaner.util.isWeChat
import java.lang.reflect.Method          // ← 补


object EntryHook : BaseHook() {
    override val hookName: String = "EntryHook"

    override fun init() {
        when {
            hostApp.isQqOrTim -> {
                initQqOrTimAbout()      // 原有关于页入口
                initQqGeneral()         // 新增通用设置页入口
            }
            hostApp.isWeChat -> {
                initWeChat()
            }
        }
    }

    private fun startModuleSettingActivity(activity: Activity) {
        val intent = Intent(activity, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        activity.startActivity(intent)
    }

    /* ---------------- 关于页入口（你原来的代码，不动） ---------------- */
    private fun initQqOrTimAbout() {
        getMethodByDesc("Lcom/tencent/mobileqq/activity/AboutActivity;->doOnCreate(Landroid/os/Bundle;)Z").also { m ->
            m.hookAfter { param ->
                val cFormSimpleItem = try {
                    loadClass("com.tencent.mobileqq.widget.FormSimpleItem")
                } catch (e: Exception) {
                    loadClass("com.tencent.mobileqq.widget.FormCommonSingleLineItem")
                }
                val vg: ViewGroup = try {
                    param.thisObject.getObjectAs("a", cFormSimpleItem)
                } catch (e: Exception) {
                    param.thisObject.getObjectOrNullByType(cFormSimpleItem) as View
                }.parent as ViewGroup

                val entry = cFormSimpleItem.newInstanceAs<View>(
                    args(param.thisObject),
                    argTypes(Context::class.java)
                )!!.also {
                    it.invokeMethod(
                        "setLeftText",
                        args("${hostAppName}瘦身"),
                        argTypes(CharSequence::class.java)
                    )
                    it.invokeMethod(
                        "setRightText",
                        args("芜狐~"),
                        argTypes(CharSequence::class.java)
                    )
                }
                entry.setOnClickListener {
                    startModuleSettingActivity(param.thisObject as Activity)
                }
                vg.addView(entry, 2)
            }
        }
    }

private fun initQqGeneral() {
    val generalClz = loadClass("com.tencent.mobileqq.activity.GeneralSettingActivity")
    generalClz.getDeclaredMethod("onCreate", Bundle::class.java).hookAfter { param ->
        val act = param.thisObject as Activity
        // 只保留这一行，显式指定泛型
        val listView = act.getObjectAs<ListView>("list", ListView::class.java)

        val itemClz = loadClass("com.tencent.mobileqq.widget.FormSimpleItem")
        val entry = itemClz.newInstanceAs<View>(args(act), argTypes(Context::class.java))!!
        entry.invokeMethod("setLeftText", args("QQCleaner 通用入口"))
        entry.invokeMethod("setRightText", args("点我~"))
        entry.setOnClickListener { startModuleSettingActivity(act) }

        (listView as ViewGroup).addView(entry, 4)   // 第 5 行（0 起算）
    }
}


    /* ---------------- 微信入口（你原来的代码，不动） ---------------- */
    private fun initWeChat() {
        runCatching {
            val actClass = try {
                loadClass("com.tencent.mm.plugin.setting.ui.setting.SettingsAboutMicroMsgUI")
            } catch (e: Exception) {
                loadClass("com.tencent.mm.ui.setting.SettingsAboutMicroMsgUI")
            }
            val preferenceClass = loadClass("com.tencent.mm.ui.base.preference.Preference")

            fun getKey(preference: Any): Any = preference.invokeMethod("getKey")
                ?: preference.getObject("mKey")

            actClass.getDeclaredMethod("onCreate", Bundle::class.java).hookAfter {
                val ctx = it.thisObject
                val listView = it.thisObject.invokeMethod("getListView") as? ListView
                    ?: it.thisObject.getObjectAs("list", ListView::class.java)
                val adapter = listView.adapter as BaseAdapter
                val addMethod: Method = findMethod(adapter.javaClass) {
                    returnType == Void.TYPE && parameterTypes.contentDeepEquals(
                        arrayOf(preferenceClass, Int::class.java)
                    )
                }
                val entry = loadClass("com.tencent.mm.ui.base.preference.IconPreference")
                    .getConstructor(Context::class.java)
                    .newInstance(ctx).apply {
                        invokeMethod("setKey", args("QQCleaner"), argTypes(String::class.java))
                        invokeMethod("setSummary", args("芜狐~"), argTypes(CharSequence::class.java))
                        invokeMethod("setTitle", args("微信瘦身"), argTypes(CharSequence::class.java))
                    }

                findMethod(adapter.javaClass) { name == "notifyDataSetChanged" }
                    .hookBefore { _ ->
                        if (adapter.count == 0) return@hookBefore
                        val pos = adapter.count - 2
                        if ("QQCleaner" != getKey(adapter.getItem(pos))) {
                            addMethod.invoke(adapter, entry, pos)
                        }
                    }
            }

            findMethod(actClass) {
                name == "onPreferenceTreeClick" &&
                    parameterTypes[1].isAssignableFrom(preferenceClass)
            }.hookBefore {
                if ("QQCleaner" == getKey(it.args[1])) {
                    startModuleSettingActivity(it.thisObject as Activity)
                    it.result = true
                }
            }
        }.logeIfThrow()
    }
}
