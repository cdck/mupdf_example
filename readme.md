2024/07/01
- 添加画笔批注后刷新时在调用到`Page.destory()`方法时有几率出现崩溃
  - 暂时添加了`try catch`包裹避免
   