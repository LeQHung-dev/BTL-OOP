/* Chế độ "chia sẻ với khách": chỉ bật được ở trang báo giá, thanh toán, hóa đơn (body có data-share).
   Khi bật: ẩn menu, tồn kho, nút nghiệp vụ; chữ to, rõ cho bệnh nhân xem qua màn hình chia sẻ.
   Trạng thái giữ qua các trang trong cùng một phiên; sang trang nội bộ (duyệt thuốc...) thì tự tắt. */
(function () {
  var KEY = 'pharmascanShare', body = document.body;
  if (!body.hasAttribute('data-share')) {
    try { sessionStorage.removeItem(KEY); } catch (e) { /* bỏ qua */ }
    return;
  }
  var on = false;
  try { on = sessionStorage.getItem(KEY) === '1'; } catch (e) { /* bỏ qua */ }

  var btn = document.createElement('button');
  btn.type = 'button';
  btn.className = 'sharebtn no-print';
  document.body.appendChild(btn);

  function render() {
    body.classList.toggle('share', on);
    btn.textContent = on ? 'Thoát chế độ khách' : 'Chia sẻ màn hình với khách';
    btn.classList.toggle('active', on);
  }
  btn.addEventListener('click', function () {
    on = !on;
    try { sessionStorage.setItem(KEY, on ? '1' : '0'); } catch (e) { /* bỏ qua */ }
    render();
  });
  render();
})();
