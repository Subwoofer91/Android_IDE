# Android IDE

一个以 Jetpack Compose 构建的阅读器/IDE 原型。主阅读界面会根据窗口宽度与方向在固定双栏和移动端抽屉之间切换；AI 助手通过供应商无关的领域接口流式响应，并明确管理上下文附件与大小限制。

## 设计约束

- 平板宽度（至少 600dp）或横屏固定显示阅读区与 AI 助手双栏；手机竖屏从底部按钮打开助手抽屉。
- AI 请求只包含用户明确附加的文件或选区，绝不会隐式上传整个项目。
- 默认限制单文件 128 KiB、总上下文 512 KiB；发送前会列出附件和估算字节数。
- `AssistantViewModel` 持有会话状态；切换会话、清空、停止以及页面离开都会取消流式请求。

## 构建

项目需要 JDK 17 和 Android SDK（API 35）：

```shell
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```
