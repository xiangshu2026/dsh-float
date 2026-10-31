# gen-tidycss.ps1 — 把 tidy.css 转成 TidyCss.java（Java 字符串常量）
#
# 用法（照抄）：
#   powershell -ExecutionPolicy Bypass -File "E:\deepseek工作区\dsh-float\gen-tidycss.ps1"
#
# 默认行为：读 E:\deepseek工作区\dsh-float\tidy.css，同时写两份 TidyCss.java：
#   C:\dshfloat\app\src\com\alpha\dshfloat\TidyCss.java                 （编译副本，权威）
#   E:\deepseek工作区\dsh-float\app\src\com\alpha\dshfloat\TidyCss.java （工作区镜像）
#
# 可选：-Css <路径>  -Out <单个目标路径>（只写一份，便于校验）
param(
  [string]$Css = 'E:\deepseek工作区\dsh-float\tidy.css',
  [string]$Out = ''
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path $Css)) { throw "找不到 CSS：$Css" }

$css = [System.IO.File]::ReadAllText($Css, [System.Text.Encoding]::UTF8)

$sb = New-Object System.Text.StringBuilder
foreach ($ch in $css.ToCharArray()) {
  if     ($ch -ceq '\')       { [void]$sb.Append('\\') }
  elseif ($ch -ceq '"')       { [void]$sb.Append('\"') }
  elseif ($ch -ceq [char]13)  { }                                  # CRLF 的 CR 丢掉，统一 LF
  elseif ($ch -ceq [char]10)  { [void]$sb.Append('\n') }
  else                        { [void]$sb.Append($ch) }
}
$lit = $sb.ToString()

$LF = [string][char]10
$header = 'package com.alpha.dshfloat;' + $LF + $LF +
          '/** 手机端精简样式：单栏 / 大字 / 藏思考 / 藏桌宠 / 藏联网球 / 会话抽屉 / 主列撑满。 */' + $LF +
          'public final class TidyCss {' + $LF + $LF +
          '    public static final String CSS =' + $LF
$footer = $LF + $LF + '    private TidyCss() {}' + $LF + '}' + $LF
$text = $header + '"' + $lit + '";' + $footer

$targets = if ($Out -ne '') { @($Out) } else {
  @('C:\dshfloat\app\src\com\alpha\dshfloat\TidyCss.java',
    'E:\deepseek工作区\dsh-float\app\src\com\alpha\dshfloat\TidyCss.java')
}
$enc = New-Object System.Text.UTF8Encoding($false)   # 无 BOM
foreach ($t in $targets) {
  $dir = Split-Path -Parent $t
  if (-not (Test-Path $dir)) { throw "目标目录不存在：$dir" }
  [System.IO.File]::WriteAllText($t, $text, $enc)
  "已写入 {0}  ({1} 字符)" -f $t, $text.Length
}
