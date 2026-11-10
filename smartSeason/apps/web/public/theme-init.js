(function () {
  var t = localStorage.getItem('ss-theme') || (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
  if (t === 'dark') document.documentElement.classList.add('dark');
})();
