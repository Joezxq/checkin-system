// 签到系统 - 通用工具函数

// HTML转义函数，防止XSS攻击
function escapeHtml(text) {
    if (text == null) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// API 请求封装
async function request(url, options = {}) {
    const defaultOptions = {
        headers: {
            'Content-Type': 'application/json'
        },
        credentials: 'include' // 携带 Cookie
    };

    const finalOptions = { ...defaultOptions, ...options };

    try {
        const response = await fetch(url, finalOptions);
        const data = await response.json();

        if (data.code === 0) {
            return data.data;
        } else {
            throw new Error(data.message || '请求失败');
        }
    } catch (error) {
        throw error;
    }
}

// GET 请求
async function get(url) {
    return request(url, { method: 'GET' });
}

// POST 请求
async function post(url, body) {
    return request(url, {
        method: 'POST',
        body: JSON.stringify(body)
    });
}

// PUT 请求
async function put(url, body) {
    return request(url, {
        method: 'PUT',
        body: JSON.stringify(body)
    });
}

// DELETE 请求
async function del(url) {
    return request(url, { method: 'DELETE' });
}

// 创建 Bootstrap 模态框
function _createBootstrapModal(title, message, type, showCancel, onConfirm) {
    const typeClass = type === 'error' ? 'danger' : type;
    const iconMap = { success: '✅', error: '❌', info: 'ℹ️' };
    const icon = iconMap[type] || 'ℹ️';

    const modalHtml = `
        <div class="modal fade" tabindex="-1">
            <div class="modal-dialog modal-dialog-centered">
                <div class="modal-content">
                    <div class="modal-header">
                        <h5 class="modal-title"></h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                    </div>
                    <div class="modal-body">
                        <div class="d-flex align-items-center gap-3">
                            <span style="font-size:2rem;">${icon}</span>
                            <p class="mb-0"></p>
                        </div>
                    </div>
                    <div class="modal-footer">
                        ${showCancel ? '<button type="button" class="btn btn-secondary" data-bs-dismiss="modal">取消</button>' : ''}
                        <button type="button" class="btn btn-${typeClass}" id="_bsConfirmBtn">确定</button>
                    </div>
                </div>
            </div>
        </div>
    `;

    const wrapper = document.createElement('div');
    wrapper.innerHTML = modalHtml;
    const modalEl = wrapper.firstElementChild;
    document.body.appendChild(modalEl);

    // 安全设置文本
    modalEl.querySelector('.modal-title').textContent = title;
    modalEl.querySelector('.modal-body p').textContent = message;

    const bsModal = new bootstrap.Modal(modalEl);

    // 确认按钮事件
    const confirmBtn = modalEl.querySelector('#_bsConfirmBtn');
    if (showCancel && onConfirm) {
        confirmBtn.addEventListener('click', () => {
            bsModal.hide();
        });
    } else {
        confirmBtn.addEventListener('click', () => {
            bsModal.hide();
        });
    }

    // 如果有关闭回调
    if (showCancel && onConfirm) {
        confirmBtn.addEventListener('click', onConfirm);
    }

    // 模态框隐藏后移除 DOM
    modalEl.addEventListener('hidden.bs.modal', () => {
        modalEl.remove();
    });

    return bsModal;
}

// 显示提示框
function showModal(title, message, type = 'info') {
    const modal = _createBootstrapModal(title, message, type, false, null);
    modal.show();
}

// 显示成功提示
function showSuccess(message) {
    showModal('成功', message, 'success');
}

// 显示错误提示
function showError(message) {
    showModal('错误', message, 'error');
}

// 显示信息提示
function showInfo(message) {
    showModal('提示', message, 'info');
}

// 显示确认对话框
function showConfirm(title, message, onConfirm) {
    const modal = _createBootstrapModal(title, message, 'info', true, onConfirm);
    modal.show();
}

// 显示 Toast 提示（非阻塞）
function showToast(message, type = 'info') {
    const bgClass = type === 'success' ? 'bg-success' : type === 'error' ? 'bg-danger' : 'bg-info';
    const toastHtml = `
        <div class="toast align-items-center text-white ${bgClass} border-0 position-fixed bottom-0 end-0 m-3" role="alert">
            <div class="d-flex">
                <div class="toast-body"></div>
                <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
            </div>
        </div>
    `;
    const wrapper = document.createElement('div');
    wrapper.innerHTML = toastHtml;
    const toastEl = wrapper.firstElementChild;
    toastEl.querySelector('.toast-body').textContent = message;
    document.body.appendChild(toastEl);

    const toast = new bootstrap.Toast(toastEl, { delay: 3000 });
    toast.show();
    toastEl.addEventListener('hidden.bs.toast', () => toastEl.remove());
}

// 表单验证
function validateForm(formId) {
    const form = document.getElementById(formId);
    const inputs = form.querySelectorAll('input[required], select[required], textarea[required]');
    let isValid = true;

    inputs.forEach(input => {
        const existingFeedback = input.parentNode.querySelector('.invalid-feedback');
        if (existingFeedback) existingFeedback.remove();
        input.classList.remove('is-invalid');

        if (!input.value.trim()) {
            isValid = false;
            input.classList.add('is-invalid');
            const feedback = document.createElement('div');
            feedback.className = 'invalid-feedback';
            feedback.textContent = input.getAttribute('data-error') || '此字段不能为空';
            input.parentNode.appendChild(feedback);
        }
    });

    return isValid;
}

// 格式化时间
function formatDateTime(dateTimeStr) {
    if (!dateTimeStr) return '';

    if (typeof dateTimeStr === 'string' && /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(dateTimeStr)) {
        return dateTimeStr;
    }

    const date = new Date(dateTimeStr);
    if (isNaN(date.getTime())) {
        return dateTimeStr;
    }

    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');
    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}

// 格式化日期
function formatDate(dateStr) {
    if (!dateStr) return '';
    return dateStr.split(' ')[0];
}

// 获取 URL 参数
function getUrlParam(name) {
    const urlParams = new URLSearchParams(window.location.search);
    return urlParams.get(name);
}

// 跳转页面
function navigateTo(url) {
    window.location.href = url;
}

// 登出
async function logout() {
    try {
        await post('/api/auth/logout');
        navigateTo('/');
    } catch (error) {
        showError(error.message);
    }
}

// 防抖函数
function debounce(fn, delay = 300) {
    let timer;
    return function (...args) {
        clearTimeout(timer);
        timer = setTimeout(() => fn.apply(this, args), delay);
    };
}

// 骨架屏注入
function skeleton(containerId, rows = 5) {
    const el = document.getElementById(containerId);
    if (!el) return;
    el.innerHTML = Array.from({length: rows}, () =>
        '<div class="skeleton skeleton-row"></div>'
    ).join('');
}

// 列表交错淡入
function animateList(selector, baseDelay = 0.05) {
    document.querySelectorAll(selector).forEach((el, i) => {
        el.style.animation = `fadeInUp 0.4s ease-out ${i * baseDelay}s both`;
    });
}

// 全局关闭拼写检查
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('input:not([type=password]):not([type=checkbox]), textarea').forEach(el => {
        el.setAttribute('spellcheck', 'false');
        el.setAttribute('autocomplete', 'off');
    });
    // 自动加载当前登录用户姓名，注入到所有页面导航栏
    loadUserName();
});

async function loadUserName() {
    try {
        const me = await get('/api/auth/me');
        const name = me.userName;
        if (!name) return;
        // 在所有 .navbar-brand 元素后插入用户名
        document.querySelectorAll('.navbar-brand').forEach(el => {
            // 避免重复插入
            if (!el.querySelector('.user-name-badge')) {
                const badge = document.createElement('span');
                badge.className = 'user-name-badge badge bg-light text-dark ms-2';
                badge.style.fontSize = '0.8rem';
                badge.style.fontWeight = 'normal';
                badge.textContent = '👤 ' + name;
                el.appendChild(badge);
            }
        });
    } catch(e) {
        // 未登录或请求失败时静默处理
    }
}
