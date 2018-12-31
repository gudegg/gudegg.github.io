---
title: fastsoso被攻击，限制访问频次
date: 2018-04-14 16:32:45
tags: fastsoso
---

> fastsoso最近被大量ip攻击，单核的服务器实在承受不起，最近想要给他加个拦截，对于每分钟访问数超过一定阈值的ip，直接进行拦截！

<!--more-->

#### redis实现访问频次控制

```java
public class IpBlockInterceptor implements HandlerInterceptor {
    private final static Logger LOGGER = LoggerFactory.getLogger(IpBlockInterceptor.class);
    private final static DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private final static long EXPIRE_TIME = TimeUnit.SECONDS.toMillis(60);
    private final static long MAX_COUNT = 100;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientIp = getClientIp(request);
        if (clientIp != null && clientIp.trim().length() > 0) {
            //ip+小时+分作为key,并且每个key有效期为1分钟
            String key = clientIp + "|" + LocalTime.now().format(DATE_TIME_FORMATTER);
            long count = 1;
            
            Boolean exist = stringRedisTemplate.hasKey(key);
            if (exist) {
                count = stringRedisTemplate.opsForValue().increment(key, 1);
            } else {
                //存在并发 同时请求可能返回都为false 所以设置键值对的时候 要用setnx而不是set setnx只有一个请求会成功设置值
                Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, "1");
                if (success) {
                    stringRedisTemplate.expire(key, EXPIRE_TIME, TimeUnit.MILLISECONDS);
                } else {
                    count = stringRedisTemplate.opsForValue().increment(key, 1);
                }

            }
            
            if (count < MAX_COUNT) {
                return true;
            }
            //超过设置的阈值，直接进行拦截
            LOGGER.debug("ip:[{}] has been block", clientIp);
        }
        return false;
    }

    @Override
    public void postHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o, ModelAndView modelAndView) throws Exception {

    }

    @Override
    public void afterCompletion(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o, Exception e) throws Exception {

    }


    private static String getClientIp(HttpServletRequest request) {

        String remoteAddr = "";

        if (request != null) {
            remoteAddr = request.getHeader("X-FORWARDED-FOR");
            if (remoteAddr == null || "".equals(remoteAddr)) {
                remoteAddr = request.getRemoteAddr();
            }
        }

        return remoteAddr;
    }
}
```