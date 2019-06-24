# CallRecord
通话记录同步记录库

###### 工作原理
1. 该库通过 PhoneStateListener 监听当前通话记录并且兼容至 Android KitKat
2. 在某些低版本机型无法通过 PhoneStateListener 的回调获取到当前通话号码，则在通话结束后通过时间范围锁定目标系统通话记录并同步数据
3. 使用 MediaRecorder 对 VOICE_COMMUNICATION 进行监听并输出为音频 (由于无法直接监听通话上下流，所以音频质量较差，目前该功能比较鸡肋)

###### 依赖库
- [Realm](httpshttps://github.com/realm)
- [RxAndroid](https://github.com/ReactiveX/RxAndroid)
- [Kotlin-Realm-Extensions](https://github.com/vicpinm/Kotlin-Realm-Extensions)
- [Timber](https://github.com/JakeWharton/timber)
- [AndLinker](https://github.com/codezjx/AndLinker)
