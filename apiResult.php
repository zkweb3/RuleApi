<?php
error_reporting(0);

$version = "1.0.0 beta";
$versionIntro = ""; //支持html代码
$versionUrl = "";
$versionCode = 10;

//公告，支持html代码

$announcement = "";


if(isset($_GET['update'])){
	$result=array(
    'version'=>$version,
    'versionIntro'=>$versionIntro,
    'versionUrl'=>$versionUrl,
	'versionCode'=>$versionCode,
	'announcement'=>$announcement
   );
   //输出json
   echo json_encode($result);
}
?>