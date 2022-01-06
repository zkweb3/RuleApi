package com.RuleApi.web;

import com.RuleApi.common.*;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.RuleApi.entity.*;
import com.RuleApi.service.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 控制层
 * TypechoUserlogController
 * 同时负责用户的收藏，点赞，打赏和签到
 * @author buxia97
 * @date 2022/01/06
 */
@Controller
@RequestMapping(value = "/typechoUserlog")
public class TypechoUserlogController {

    @Autowired
    TypechoUserlogService service;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private TypechoRelationshipsService relationshipsService;

    @Autowired
    private TypechoMetasService metasService;

    @Autowired
    private TypechoContentsService contentsService;

    @Autowired
    private TypechoFieldsService fieldsService;

    @Autowired
    private TypechoUsersService usersService;

    RedisHelp redisHelp =new RedisHelp();
    ResultAll Result = new ResultAll();
    HttpClient HttpClient = new HttpClient();
    UserStatus UStatus = new UserStatus();

    baseFull baseFull = new baseFull();
    /***
     * 表单查询请求
     * @param searchParams Bean对象JSON字符串
     * @param page         页码
     * @param limit        每页显示数量
     */
    @RequestMapping(value = "/userlogList")
    @ResponseBody
    public String userlogList (@RequestParam(value = "searchParams", required = false) String  searchParams,
                            @RequestParam(value = "page"        , required = false, defaultValue = "1") Integer page,
                            @RequestParam(value = "limit"       , required = false, defaultValue = "15") Integer limit,
                            @RequestParam(value = "token", required = false) String  token) {


        Integer uStatus = UStatus.getStatus(token,redisTemplate);
        if(uStatus==0){
            return Result.getResultJson(0,"用户未登录或Token验证失败",null);
        }
        Map map =redisHelp.getMapValue("userInfo"+token,redisTemplate);
        Integer uid =Integer.parseInt(map.get("uid").toString());

        TypechoUserlog query = new TypechoUserlog();
        if (StringUtils.isNotBlank(searchParams)) {
            JSONObject object = JSON.parseObject(searchParams);
            query = object.toJavaObject(TypechoUserlog.class);
        }


        List jsonList = new ArrayList();
        List cacheList = redisHelp.getList("userlogList_"+page+"_"+limit+"_"+searchParams+"_"+uid,redisTemplate);
        try{
            if(cacheList.size()>0){
                jsonList = cacheList;
            }else {
                PageList<TypechoUserlog> pageList = service.selectPage(query, page, limit);
                List<TypechoUserlog> list = pageList.getList();
                for (int i = 0; i < list.size(); i++) {
                    Integer cid = list.get(i).getCid();

                    TypechoContents typechoContents = contentsService.selectByKey(cid);
                    Map contentsInfo = JSONObject.parseObject(JSONObject.toJSONString(typechoContents), Map.class);
                    //只有开放状态文章允许加入
                    String status = contentsInfo.get("status").toString();
                    String ctype = contentsInfo.get("type").toString();
                    //应该判断类型和发布状态，而不是直接判断状态
                    if (status.equals("publish") && ctype.equals("post")) {
                        //处理文章内容为简介

                        String text = contentsInfo.get("text").toString();
                        List imgList = baseFull.getImageSrc(text);
                        text = text.replaceAll("(\\\r\\\n|\\\r|\\\n|\\\n\\\r)", "");
                        text = text.replaceAll("\\s*", "");
                        text = text.replaceAll("</?[^>]+>", "");
                        //去掉文章开头的图片插入
                        text = text.replaceAll("((!\\[)[\\s\\S]+?(\\]\\[)[\\s\\S]+?(\\]))+?", "");
                        contentsInfo.put("text", text.length() > 200 ? text.substring(0, 200) : text);
                        contentsInfo.put("images", imgList);
                        //加入自定义字段，分类和标签
                        //加入自定义字段信息，这里取消注释即可开启，但是数据库查询会消耗性能
                        List<TypechoFields> fields = fieldsService.selectByKey(cid);
                        contentsInfo.put("fields", fields);

                        List<TypechoRelationships> relationships = relationshipsService.selectByKey(cid);

                        List metas = new ArrayList();
                        List tags = new ArrayList();
                        for (int j = 0; j < relationships.size(); j++) {
                            Map info = JSONObject.parseObject(JSONObject.toJSONString(relationships.get(j)), Map.class);
                            if (info != null) {
                                String mid = info.get("mid").toString();

                                TypechoMetas metasList = metasService.selectByKey(mid);
                                Map metasInfo = JSONObject.parseObject(JSONObject.toJSONString(metasList), Map.class);
                                String type = metasInfo.get("type").toString();
                                if (type.equals("category")) {
                                    metas.add(metasInfo);
                                }
                                if (type.equals("tag")) {
                                    tags.add(metasInfo);
                                }
                            }

                        }

                        contentsInfo.remove("password");
                        contentsInfo.put("category", metas);
                        contentsInfo.put("tag", tags);

                        jsonList.add(contentsInfo);
                    }


                }
                redisHelp.delete("userlogList_" + page + "_" + limit + "_" + searchParams + "_" + uid, redisTemplate);
                redisHelp.setList("userlogList_" + page + "_" + limit + "_" + searchParams + "_" + uid, jsonList, 5, redisTemplate);
            }
        }catch (Exception e){
            if(cacheList.size()>0){
                jsonList = cacheList;
            }
        }
        JSONObject response = new JSONObject();
        response.put("code" , 1);
        response.put("msg"  , "");
        response.put("data" , null != jsonList ? jsonList : new JSONArray());
        response.put("count", jsonList.size());
        return response.toString();
    }


    /***
     * 添加log
     * @param params Bean对象JSON字符串
     */
    @RequestMapping(value = "/addLog")
    @ResponseBody
    public String addLog(@RequestParam(value = "params", required = false) String  params,@RequestParam(value = "token", required = false) String  token,HttpServletRequest request) {
        Integer uStatus = UStatus.getStatus(token,redisTemplate);
        if(uStatus==0){
            return Result.getResultJson(0,"用户未登录或Token验证失败",null);
        }
        Map jsonToMap =null;
        TypechoUserlog insert = null;
        String  agent =  request.getHeader("User-Agent");
        String  ip = baseFull.getIpAddr(request);

        if (StringUtils.isNotBlank(params)) {
            Map map =redisHelp.getMapValue("userInfo"+token,redisTemplate);
            Integer uid =Integer.parseInt(map.get("uid").toString());

            jsonToMap =  JSONObject.parseObject(JSON.parseObject(params).toString());
            jsonToMap.put("uid",uid);
            //生成typecho数据库格式的修改时间戳
            Long date = System.currentTimeMillis();
            String userTime = String.valueOf(date).substring(0,10);
            jsonToMap.put("created",userTime);
            String type = jsonToMap.get("type").toString();
            //mark为收藏，reward为打赏，likes为奖励，clock为签到
            if(!type.equals("mark")&&!type.equals("reward")&&!type.equals("likes")&&!type.equals("clock")){
                return Result.getResultJson(0,"错误的字段类型",null);
            }
            //如果是点赞，那么每天只能一次
            if(type.equals("likes")){
                String isLikes = redisHelp.getRedis("userlikes"+"_"+ip+"_"+agent,redisTemplate);
                if(isLikes!=null){
                    return Result.getResultJson(0,"距离上次操作不到24小时！",null);
                }
                redisHelp.setRedis("userlikes"+"_"+ip+"_"+agent,"yes",86400,redisTemplate);
            }
            //签到，每天一次
            if(type.equals("clock")){
                TypechoUserlog log = new TypechoUserlog();
                log.setType("clock");
                log.setUid(uid);
                List<TypechoUserlog> info = service.selectList(log);

                //获取上次时间
                Integer time = info.get(0).getCreated();
                Integer timeStamp = time*1000;
                SimpleDateFormat sdf=new SimpleDateFormat("yyyyMMdd");
                String oldtime = sdf.format(new Date(Long.parseLong(String.valueOf(timeStamp))));
                Integer old = Integer.parseInt(oldtime);
                //获取本次时间
                Long curStamp = System.currentTimeMillis();  //获取当前时间戳
                String curtime = sdf.format(new Date(Long.parseLong(String.valueOf(curStamp))));
                Integer cur = Integer.parseInt(curtime);
                if(old>=cur){
                    return Result.getResultJson(0,"你已经签到过了哦",null);
                }
            }
            //收藏，只能一次
            if(type.equals("mark")){
                if(jsonToMap.get("cid")==null){
                    return Result.getResultJson(0,"参数不正确",null);
                }
                Integer cid = Integer.parseInt(jsonToMap.get("cid").toString());
                TypechoUserlog log = new TypechoUserlog();
                log.setType("mark");
                log.setUid(uid);
                log.setCid(cid);
                List<TypechoUserlog> info = service.selectList(log);
                if(info.size()>0){
                    return Result.getResultJson(0,"已在你的收藏中！",null);
                }
            }
            //打赏，要扣余额
            if(type.equals("reward")){
                if(jsonToMap.get("num")==null){
                    return Result.getResultJson(0,"参数不正确",null);
                }
                Integer num = Integer.parseInt(jsonToMap.get("num").toString());
                TypechoUsers user = usersService.selectByKey(uid);
                Integer account = user.getAccount();
                if(num>account){
                    return Result.getResultJson(0,"积分不足！",null);
                }
            }
            insert = JSON.parseObject(JSON.toJSONString(jsonToMap), TypechoUserlog.class);
        }

        int rows = service.insert(insert);

        JSONObject response = new JSONObject();
        response.put("code" , rows);
        response.put("msg"  , rows > 0 ? "操作成功" : "操作失败");
        return response.toString();
    }

    /***
     * 表单删除
     */
    @RequestMapping(value = "/removeLog")
    @ResponseBody
    public String removeLog(@RequestParam(value = "key", required = false) String  key,@RequestParam(value = "token", required = false) String  token) {
        Integer uStatus = UStatus.getStatus(token,redisTemplate);
        if(uStatus==0){
            return Result.getResultJson(0,"用户未登录或Token验证失败",null);
        }
        //验证用户权限
        Map map =redisHelp.getMapValue("userInfo"+token,redisTemplate);
        Integer uid =Integer.parseInt(map.get("uid").toString());
        String group = map.get("group").toString();
        if(!group.equals("administrator")){
            TypechoUserlog info = service.selectByKey(key);
            Integer userId = info.getUid();
            if(uid!=userId){
                return Result.getResultJson(0,"你无权进行此操作",null);
            }
        }

        Integer rows =  service.delete(key);
        JSONObject response = new JSONObject();
        response.put("code" , rows);
        response.put("msg"  , rows > 0 ? "操作成功" : "操作失败");
        return response.toString();

    }
}
