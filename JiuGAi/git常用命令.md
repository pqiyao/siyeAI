当然。你现在最常用的 Git 命令就这几类，够你日常上传 GitHub 了。

**1. 进入项目目录**

```
cd "C:\Users\yao\Desktop\AiSillyTavern\JiuGAi\原版（暗黑配色）\h5" 
```

以后所有 Git 命令都在项目目录里执行。

**2. 查看当前状态**

```
git status 
```

看哪些文件改了、哪些文件还没提交。

**3. 添加文件到暂存区**

添加全部改动：

```
git add . 
```

只添加某个文件：

```
git add README.md 
```

只添加截图目录：

```
git add screenshots 
```

**4. 提交到本地仓库**

```
git commit -m "docs: 更新项目说明" 
```

提交信息可以这样写：

```
git commit -m "feat: 添加演示截图" git commit -m "fix: 修复页面文案" git commit -m "docs: 更新README" git commit -m "chore: 清理无用文件" 
```

**5. 推送到 GitHub**

```
git push origin main 
```

你的当前仓库已经连好了，所以一般就是这句。

**6. 日常完整流程**

以后你改完文件，就这样：

```
cd "C:\Users\yao\Desktop\AiSillyTavern\JiuGAi\原版（暗黑配色）\h5" git status git add . git commit -m "docs: 更新项目展示" git push origin main 
```

**7. 查看提交记录**

```
git log --oneline 
```

会看到类似：

```
30ac0b1 docs: 添加项目演示截图 d7f2704 Initial commit: 上传项目代码 
```

**8. 查看远程仓库地址**

```
git remote -v 
```

你现在主要用的是：

```
origin  https://github.com/pqiyao/siyeAI.git 
```

**9. 修改远程仓库地址**

```
git remote set-url origin https://github.com/用户名/仓库名.git 
```

比如：

```
git remote set-url origin https://github.com/pqiyao/siyeAI.git 
```

**10. 第一次初始化新项目**

以后新项目第一次上传，用这一套：

```
git init git add . git commit -m "Initial commit" git branch -M main git remote add origin https://github.com/用户名/仓库名.git git push -u origin main 
```

你记住最核心的四句就够了：

```
git status git add . git commit -m "说明这次改了什么" git push origin main 
```

简单理解：

```
git status  = 看情况 git add     = 准备上传 git commit  = 打包成一次记录 git push    = 推到 GitHub
```