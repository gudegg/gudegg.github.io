<div class="copyright" >
  {% set current = date(Date.now(), "YYYY") %}
  &copy; {% if theme.since and theme.since != current %} {{ theme.since }} - {% endif %}
  <span itemprop="copyrightYear">{{ current }}</span>
  <span class="with-love">
    <i class="fa fa-heart"></i>
  </span>
  <span class="author" itemprop="copyrightHolder">{{ config.author }}
  
  &nbsp;&nbsp;|&nbsp;&nbsp;<span><a href="/sitemap.xml">Google网站地图</a></span>
  &nbsp;&nbsp;|&nbsp;&nbsp;<span><a href="/baidusitemap.xml">百度网站地图</a></span>
  &nbsp;&nbsp;|&nbsp;&nbsp;Hosted by <a href="https://pages.coding.me" style="font-weight: bold">Coding Pages</a>
  </span>
</div>

<div class="powered-by">
  {{ __('footer.powered', '<a class="theme-link" href="http://hexo.io">Hexo</a>') }}
</div>

<div class="theme-info">
  {{ __('footer.theme') }} -
  <a class="theme-link" href="https://github.com/iissnan/hexo-theme-next">
    NexT.{{ theme.scheme }}
  </a>
</div>




&nbsp;&nbsp;|&nbsp;&nbsp;<span id="busuanzi_container_site_pv">本站总访问量<span id="busuanzi_value_site_pv"></span>次</span>
&nbsp;&nbsp;|&nbsp;&nbsp;您是第 <span id="busuanzi_value_site_uv"></span> 位访客

<script async src="//busuanzi.ibruce.info/busuanzi/2.3/busuanzi.pure.mini.js"></script>
</script>

<!--百度自动推送-->
<script>
(function(){
    var bp = document.createElement('script');
    var curProtocol = window.location.protocol.split(':')[0];
    if (curProtocol === 'https') {
        bp.src = 'https://zz.bdstatic.com/linksubmit/push.js';        
    }
    else {
        bp.src = 'http://push.zhanzhang.baidu.com/push.js';
    }
    var s = document.getElementsByTagName("script")[0];
    s.parentNode.insertBefore(bp, s);
})();
</script>

{% block footer %}{% endblock %}
