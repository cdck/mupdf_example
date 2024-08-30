[JitPack版本](https://jitpack.io/#cdck/mupdf_example)

### 2024/07/05
- 签名期间禁止缩放页面和切换上一页下一页，解决7月1日的崩溃问题
- 签名区域拖动使用图标更加显眼
### 2024/07/01
- 添加画笔批注后刷新时在调用到`Page.destory()`方法时有几率出现崩溃
  - 暂时添加了`try catch`包裹避免
   