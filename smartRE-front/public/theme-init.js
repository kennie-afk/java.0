(function () {
  var t = localStorage.getItem('sre-theme') || (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
  if (t === 'dark') document.documentElement.classList.add('dark');
})();
