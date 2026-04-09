// ==========================================
// 全局配置和状态
// ==========================================
// 前后端联调：通过 Nginx 同源部署后，API 走同源 /api，避免跨域与写死 IP。
// 约定：Nginx 反代 /api -> http://127.0.0.1:8080
const API_BASE_URL = '';
let currentPage = 1;
const pageSize = 20;
let cart = [];
let currentMember = null;
let currentPayType = null;
let products = [];
let materials = [];
let allMembers = [];
let filteredMembers = [];
let currentController = null;  // 用于取消上一个请求
let isLoading = false;          // 加载锁
let pendingRequests = new Set();
let loadingStates = new Map();
let tabSwitchTimer = null;
let currentTab = null;
let currentOrderFilter = 'all';
let currentAdjustMaterialId = null;

function getToken() {
    // 先检查 localStorage
    let token = localStorage.getItem('token');
    if (token) {
        console.log('Token from localStorage:', token);
        return token;
    }

    // 再检查 URL 参数（首次登录跳转时）
    const urlParams = new URLSearchParams(window.location.search);
    const tokenFromUrl = urlParams.get('token');
    if (tokenFromUrl) {
        console.log('Token from URL:', tokenFromUrl);
        localStorage.setItem('token', tokenFromUrl);
        // 清除 URL 中的 token，但不刷新页面
        window.history.replaceState({}, document.title, window.location.pathname);
        return tokenFromUrl;
    }

    console.log('No token found!');
    return null;
}

// 带 token 的 fetch 封装
async function fetchWithToken(url, options = {}) {
    const token = getToken();
    if (!token) {
        console.error('No token found');
        window.location.href = '/html/Login.html';
        throw new Error('No token');
    }

    const headers = {
        'Content-Type': 'application/json',
        'X-Token': token,
        ...options.headers
    };

    try {
        const response = await fetch(url, { ...options, headers });

        // 处理 500 错误，不抛异常，返回 null 让调用方处理
        if (response.status === 500) {
            console.error('Server 500 error:', url);
            const text = await response.text();
            console.error('Server response:', text);
            return null;  // 返回 null 表示服务器错误
        }

        if (response.status === 401) {
            console.error('401 Unauthorized');
            localStorage.removeItem('token');
            window.location.href = '/html/Login.html';
            throw new Error('Unauthorized');
        }

        return response;

    } catch (error) {
        if (error.name === 'AbortError') {
            console.log('Request aborted:', url);
            return null;
        }
        console.error('Fetch error:', error.message);
        throw error;
    }
}

// ==========================================
// 页面加载时验证 token
// ==========================================
document.addEventListener('DOMContentLoaded', () => {
    console.log('Page loaded, checking token...');
    const token = getToken();

    if (!token) {
        console.error('No token, redirecting to login');
        window.location.href = '/html/Login.html';
        return;
    }

    console.log('Token valid, initializing dashboard...');
    initDashboard();
    setupEventListeners();
});

// ==========================================
// 页面切换
// ==========================================
function switchTab(tabName, navElement) {
    // 如果已经在该页面，不重复加载
    if (currentTab === tabName) {
        console.log('Already on', tabName, 'skipping');
        return;
    }
    currentTab = tabName;

    // 1. 先切换 UI
    document.querySelectorAll('#mainContent > div').forEach(div => div.classList.add('hidden'));
    document.getElementById(tabName).classList.remove('hidden');

    // 2. 取消上一个请求
    if (currentController) {
        console.log('Aborting previous requests...');
        currentController.abort();
    }

    // 3. 清除之前的延时定时器
    if (tabSwitchTimer) {
        clearTimeout(tabSwitchTimer);
    }

    // 4. 创建新的 controller
    currentController = new AbortController();
    const signal = currentController.signal;

    // 5. 更新导航
    document.querySelectorAll('.nav-item').forEach(item => item.classList.remove('active'));
    if (navElement) navElement.classList.add('active');

    // 6. 更新标题
    const titles = {
        'dashboard': '工作台', 'pos': '收银点单',
        'orders': '订单管理', 'productManage': '商品管理', 'members': '会员中心',
        'inventory': '库存管理', 'purchase': '采购管理',
        'reports': '营业报表'
    };
    document.getElementById('pageTitle').textContent = titles[tabName] || '工作台';

    // 7. 延时 300ms 再加载数据，避免快速切换导致的问题
    tabSwitchTimer = setTimeout(() => {
        loadTabData(tabName, signal);
    }, 300);
}

// 统一的数据加载入口
async function loadTabData(tabName, signal) {
    // 检查 signal 是否已被取消（快速切换时）
    if (signal.aborted) {
        console.log('Signal already aborted, skipping load');
        return;
    }

    showPageLoading(tabName);

    try {
        switch(tabName) {
            case 'dashboard': await loadDashboardData(signal); break;
            case 'pos': await loadPosData(signal); break;
            case 'orders': await loadOrdersData(1, signal); break;
            case 'productManage': await loadProductManageData(signal); break;
            case 'members': await loadMembersData(signal); break;
            case 'inventory': await loadInventoryData(signal); break;
            case 'purchase': await loadPurchaseData(signal); break;
            case 'reports': await loadReportsData(signal); break;
        }
    } catch (error) {
        if (error.name === 'AbortError') {
            console.log('Request aborted for', tabName);
            return;
        }
        console.error(`加载 ${tabName} 失败:`, error);
        showToast('加载失败，请重试', 'error');
    } finally {
        hidePageLoading(tabName);
    }
}

// ==========================================
// 工作台功能
// ==========================================
function initDashboard() {
    const now = new Date();
    const dateStr = `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日 ${['日','一','二','三','四','五','六'][now.getDay()]}`;
    document.getElementById('currentDate').textContent = dateStr;
    document.getElementById('purchaseDate').valueAsDate = new Date();

    loadDashboardData();
}

async function loadDashboardData(signal = null) {
    if (signal?.aborted) return;

    // 显示加载状态
    showDashboardLoading();

    try {
        const response = await fetchWithToken(
            `${API_BASE_URL}/api/dashboard/summary`,
            { signal }
        );
        if (!response) {
            hideDashboardLoading();
            return;
        }
        if (signal?.aborted) {
            hideDashboardLoading();
            return;
        }

        const dashboard = document.getElementById('dashboard');
        if (!dashboard || dashboard.classList.contains('hidden')) {
            console.log('Dashboard hidden, discarding response');
            hideDashboardLoading();
            return;
        }

        const data = await response.json();
        if (signal?.aborted) {
            hideDashboardLoading();
            return;
        }
        if (dashboard.classList.contains('hidden')) {
            hideDashboardLoading();
            return;
        }

        // 更新 DOM
        document.getElementById('todayRevenue').textContent = `¥${data.todayRevenue?.toFixed(2) || '0.00'}`;
        document.getElementById('revenueGrowth').textContent = `${data.revenueGrowth || 0}%`;
        document.getElementById('todayOrders').textContent = data.todayOrders || 0;
        document.getElementById('ordersGrowth').textContent = `${data.ordersGrowth || 0}%`;
        document.getElementById('newMembers').textContent = data.newMembers || 0;
        document.getElementById('totalMembers').textContent = data.totalMembers || 0;
        document.getElementById('lowStockCount').textContent = data.lowStockCount || 0;

        // 串行加载其他数据
        console.log('开始加载图表数据...');

        try {
            await loadTopProducts(signal);
        } catch (e) {
            if (e.name !== 'AbortError') console.error('TopProducts 失败:', e);
        }

        if (signal?.aborted) {
            hideDashboardLoading();
            return;
        }

        try {
            await loadRecentOrders(signal);
        } catch (e) {
            if (e.name !== 'AbortError') console.error('RecentOrders 失败:', e);
        }

        if (signal?.aborted) {
            hideDashboardLoading();
            return;
        }

        try {
            await loadRevenueChart(7, signal);
        } catch (e) {
            if (e.name !== 'AbortError') console.error('RevenueChart 失败:', e);
        }

    } catch (error) {
        if (error.name === 'AbortError') {
            hideDashboardLoading();
            return;
        }
        // 显示错误状态
        showDashboardError(error.message);
        throw error;
    } finally {
        // 隐藏加载状态
        hideDashboardLoading();
    }
}

function showDashboardLoading() {
    // 给各个区域添加加载动画
    const areas = [
        { id: 'topProducts', msg: '加载热销商品...' },
        { id: 'revenueChart', msg: '加载图表...' },
        { id: 'recentOrders', msg: '加载订单...' }
    ];

    areas.forEach(area => {
        const el = document.getElementById(area.id);
        if (el) {
            el.innerHTML = `
                <div class="flex flex-col items-center justify-center py-8 text-amber-600">
                    <i class="fas fa-spinner fa-spin text-2xl mb-2"></i>
                    <span class="text-sm">${area.msg}</span>
                </div>
            `;
        }
    });
}

function hideDashboardLoading() {
    // 加载完成后，内容由各个 load 函数填充，这里不需要操作
    // 如果需要移除加载动画，可以在这里处理
}

function showDashboardError(message) {
    const areas = ['topProducts', 'revenueChart', 'recentOrders'];

    areas.forEach(id => {
        const el = document.getElementById(id);
        if (el && el.innerHTML.includes('fa-spinner')) {
            // 只有还在加载状态的才显示错误
            el.innerHTML = `
                <div class="flex flex-col items-center justify-center py-8 text-red-500">
                    <i class="fas fa-exclamation-circle text-2xl mb-2"></i>
                    <span class="text-sm">加载失败</span>
                    <button onclick="loadDashboardData()" class="mt-2 px-3 py-1 bg-amber-500 text-white text-xs rounded hover:bg-amber-600">
                        重试
                    </button>
                </div>
            `;
        }
    });
}

async function loadTopProducts(signal = null) {
    try {
        // 前置检查
        if (signal?.aborted) return;

        const response = await fetchWithToken(`${API_BASE_URL}/api/v_product_rank?limit=5`, { signal });
        if (!response) return;

        if (signal?.aborted) return;

        // 检查页面是否还显示
        const dashboard = document.getElementById('dashboard');
        if (!dashboard || dashboard.classList.contains('hidden')) return;

        const result = await response.json();
        if (!result.success) throw new Error(result.msg);

        if (signal?.aborted) return;

        const data = result.data;
        const container = document.getElementById('topProducts');

        if (!container) return; // 防御性检查

        if (data.length === 0) {
            container.innerHTML = '<div class="text-center text-amber-400 py-4">暂无数据</div>';
            return;
        }

        container.innerHTML = data.map((product, index) => `
            <div class="flex items-center justify-between p-3 ${index < 3 ? 'bg-amber-50' : 'bg-amber-50/30'} rounded-lg">
                <div class="flex items-center gap-3">
                    <span class="w-6 h-6 rounded-full ${index < 3 ? 'bg-amber-500' : 'bg-amber-300'} text-white text-xs flex items-center justify-center font-bold">
                        ${index + 1}
                    </span>
                    <span class="text-sm font-medium text-amber-900">${product.name}</span>
                </div>
                <span class="text-sm text-amber-600">${product.total_sold}杯</span>
            </div>
        `).join('');

    } catch (error) {
        if (error.name === 'AbortError') return;
        console.error('加载热销商品失败:', error);
        const container = document.getElementById('topProducts');
        if (container) container.innerHTML = '<div class="text-center text-red-400 py-4">加载失败</div>';
    }
}

async function loadRecentOrders(signal = null) {
    const tbody = document.getElementById('recentOrders');
    if (!tbody) return;

    // 显示加载状态
    tbody.innerHTML = `
        <tr>
            <td colspan="7" class="text-center py-8 text-amber-600">
                <i class="fas fa-spinner fa-spin text-xl mb-2 block"></i>
                <span class="text-sm">加载订单...</span>
            </td>
        </tr>
    `;

    try {
        if (signal?.aborted) return;

        const response = await fetchWithToken(`${API_BASE_URL}/api/sale_order/recent?limit=5`, { signal });
        if (!response) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center text-red-400 py-8">加载失败</td></tr>';
            return;
        }

        if (signal?.aborted) return;

        const dashboard = document.getElementById('dashboard');
        if (!dashboard || dashboard.classList.contains('hidden')) return;

        const result = await response.json();
        if (!result.success) throw new Error(result.msg);

        if (signal?.aborted) return;

        const orders = result.data;

        if (orders.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center text-amber-400 py-8">暂无订单</td></tr>';
            return;
        }

        tbody.innerHTML = orders.map(order => `
            <tr>
                <td class="py-4 px-6 font-mono text-sm text-amber-700">${order.order_no}</td>
                <td class="py-4 px-6">
                    <div class="flex items-center gap-2">
                        <div class="w-8 h-8 rounded-full ${order.member_id ? 'bg-amber-200' : 'bg-gray-200'} flex items-center justify-center text-xs font-bold ${order.member_id ? 'text-amber-800' : 'text-gray-600'}">
                            ${order.member_name ? order.member_name[0] : '散'}
                        </div>
                        <span class="text-sm">${order.member_name || '散客'} ${order.member_level ? '(VIP)' : ''}</span>
                    </div>
                </td>
                <td class="py-4 px-6 text-sm text-gray-600">${order.items_summary}</td>
                <td class="py-4 px-6 font-bold text-amber-900">¥${order.pay_amount.toFixed(2)}</td>
                <td class="py-4 px-6 w-28"><span class="status-badge ${getStatusClass(order.status)}">${getStatusText(order.status)}</span></td>
                <td class="py-4 px-6 text-sm text-gray-500">${order.created_at}</td>
                <td class="py-4 px-6">
                    <button class="text-amber-600 hover:text-amber-800 text-sm font-medium" onclick="viewOrderDetail('${order.order_no}')">详情</button>
                </td>
            </tr>
        `).join('');

    } catch (error) {
        if (error.name === 'AbortError') return;
        console.error('加载最近订单失败:', error);
        tbody.innerHTML = `<tr><td colspan="7" class="text-center text-red-400 py-8">加载失败: ${error.message}</td></tr>`;
    }
}

async function loadRevenueChart(days, signal = null) {
    const container = document.getElementById('revenueChart');
    if (!container) return;

    // 显示加载状态
    container.innerHTML = `
        <div class="w-full h-full flex flex-col items-center justify-center text-amber-600">
            <i class="fas fa-spinner fa-spin text-2xl mb-2"></i>
            <span class="text-sm">加载图表...</span>
        </div>
    `;

    try {
        if (signal?.aborted) return;

        const response = await fetchWithToken(`${API_BASE_URL}/api/reports/revenue-trend?days=${days}`, { signal });
        if (!response) {
            container.innerHTML = '<div class="text-center text-red-400 py-8">加载失败</div>';
            return;
        }

        if (signal?.aborted) return;

        const dashboard = document.getElementById('dashboard');
        if (!dashboard || dashboard.classList.contains('hidden')) return;

        const result = await response.json();
        if (!result.success) throw new Error(result.msg);

        if (signal?.aborted) return;

        const chartData = result.data;
        const maxValue = Math.max(...chartData.data, 1);
        const colors = ['bg-amber-200', 'bg-amber-300', 'bg-amber-400', 'bg-amber-500', 'bg-amber-600', 'bg-amber-500', 'bg-amber-400'];

        container.innerHTML = chartData.labels.map((label, index) => {
            const value = chartData.data[index];
            const height = (value / maxValue * 100) || 5;
            return `
                <div class="flex-1 flex flex-col justify-end items-center group cursor-pointer h-full">
                    <div class="mb-2 opacity-0 group-hover:opacity-100 transition-opacity bg-amber-800 text-white text-xs py-1 px-2 rounded whitespace-nowrap">
                        ¥${value.toLocaleString()}
                    </div>
                    <div class="w-full ${colors[index % colors.length]} rounded-t-lg" style="height: ${Math.max(height, 10)}%"></div>
                    <div class="mt-2 text-xs text-amber-700">${label}</div>
                </div>
            `;
        }).join('');

    } catch (error) {
        if (error.name === 'AbortError') return;
        console.error('加载营收图表失败:', error);
        container.innerHTML = `<div class="text-center text-red-400 py-8">加载失败: ${error.message}</div>`;
    }
}

// ==========================================
// 加载 POS 数据（收银点单页面）
// ==========================================
async function loadPosData(signal = null) {
    console.log('Loading POS data...');
    const productGrid = document.getElementById('productGrid');

    // 显示加载中
    productGrid.innerHTML = `
            <div class="col-span-full text-center py-8 text-amber-600">
                <i class="fas fa-spinner fa-spin text-2xl mb-2"></i>
                <p>加载商品中...</p>
            </div>
        `;

    try {
        const url = `${API_BASE_URL}/api/product?status=1`;
        console.log('Request URL:', url);

        const response = await fetchWithToken(url, { signal });
        if (!response) return;  // 被 401 拦截了

        if (document.getElementById('pos').classList.contains('hidden')) return;

        const result = await response.json();
        console.log('API Response:', result);

        // 关键修复：取 result.data
        if (!result.success) {
            throw new Error(result.msg || 'API returned error');
        }

        products = result.data || [];
        console.log('Products array:', products);
        console.log('Products count:', products.length);

        if (products.length === 0) {
            productGrid.innerHTML = `
                    <div class="col-span-full text-center py-8 text-amber-400">
                        <i class="fas fa-coffee text-4xl mb-2"></i>
                        <p>暂无在售商品</p>
                        <p class="text-xs mt-2">请检查数据库是否有 status=1 的商品</p>
                    </div>
                `;
            return;
        }

        renderProducts(products);

    } catch (error) {
        if (error.name === 'AbortError') return;
        console.error('加载商品失败:', error);
        productGrid.innerHTML = `
                <div class="col-span-full text-center py-8 text-red-500">
                    <i class="fas fa-exclamation-circle text-4xl mb-2"></i>
                    <p>商品加载失败</p>
                    <p class="text-xs mt-2">${error.message}</p>
                    <button onclick="loadPosData()" class="mt-4 px-4 py-2 bg-amber-500 text-white rounded-lg">
                        重试
                    </button>
                </div>
            `;
        showToast('商品加载失败: ' + error.message, 'error');
    }
}

// ==========================================
// 渲染商品（修复版）
// ==========================================
function renderProducts(productList) {
    const container = document.getElementById('productGrid');
    // 防御性检查
    if (!container) {
        console.warn('productGrid not found in DOM');
        return;
    }

    // 检查当前是否在 POS 页面
    const posPage = document.getElementById('pos');
    if (!posPage || posPage.classList.contains('hidden')) {
        console.log('POS page hidden, skip rendering');
        return;
    }
    console.log('Rendering products:', productList);

    if (!productList || productList.length === 0) {
        container.innerHTML = `
                <div class="col-span-full text-center text-amber-400 py-8">
                    <i class="fas fa-coffee text-4xl mb-2"></i>
                    <p>暂无在售商品</p>
                </div>
            `;
        return;
    }

    // 检查商品数据结构
    const sample = productList[0];
    console.log('Sample product:', sample);
    console.log('Has id:', 'id' in sample);
    console.log('Has name:', 'name' in sample);
    console.log('Has category:', 'category' in sample);
    console.log('Has sale_price:', 'sale_price' in sample);

    container.innerHTML = productList.map(product => {
        // 数据兼容处理
        const id = product.id;
        const name = product.name || '未命名商品';
        const category = product.category || '未分类';
        const productTag = product.product_tag || '常规商品';
        const price = product.sale_price || product.price || 0;

        return `
                <div class="product-card bg-white rounded-xl border border-amber-200 p-4 cursor-pointer hover:shadow-lg transition-all"
                     onclick="addToCart(${id})">
                    <div class="aspect-square rounded-lg bg-gradient-to-br from-amber-100 to-orange-100 mb-3 flex items-center justify-center text-4xl">
                        ${getProductIcon(category)}
                    </div>
                    <h4 class="font-bold text-amber-900 text-sm mb-1 truncate">${name}</h4>
                    <p class="text-xs text-amber-600 mb-1">${category}</p>
                    <div class="flex items-center justify-between mb-2">
                        <span class="text-[11px] px-2 py-0.5 rounded-full ${productTag === '常规商品' ? 'bg-gray-100 text-gray-600' : 'bg-orange-100 text-orange-700'}">${productTag}</span>
                        <button onclick="event.stopPropagation();deleteProduct(${id}, '${name.replace(/'/g, "\\'")}')" class="text-red-400 hover:text-red-600" title="删除商品" aria-label="删除商品">
                            <i class="fas fa-trash text-sm"></i>
                        </button>
                    </div>
                    <div class="flex justify-between items-center">
                        <span class="text-lg font-bold text-amber-600">¥${parseFloat(price).toFixed(2)}</span>
                        <i class="fas fa-plus-circle text-amber-400 text-xl hover:text-amber-600"></i>
                    </div>
                </div>
            `;
    }).join('');

    console.log('Rendered', productList.length, 'products');
}

function getProductIcon(category) {
    const icons = { '奶茶': '🧋', '果茶': '🍹', '咖啡': '☕', '甜品': '🍰' };
    return icons[category] || '🥤';
}

function filterProducts(category) {
    // 1. 过滤商品
    if (category === 'all') {
        renderProducts(products);
    } else if (category === '当季限定' || category === '地区限定') {
        renderProducts(products.filter(p => (p.product_tag || '常规商品') === category));
    } else {
        renderProducts(products.filter(p => p.category === category));
    }

    // 2. 更新按钮样式
    document.querySelectorAll('.category-btn').forEach(btn => {
        const btnCategory = btn.getAttribute('data-category');

        if (btnCategory === category) {
            // 选中状态：琥珀色背景，白色文字
            btn.classList.remove('bg-white', 'text-amber-700', 'border', 'border-amber-200', 'hover:bg-amber-50');
            btn.classList.add('bg-amber-500', 'text-white');
        } else {
            // 未选中状态：白色背景，琥珀色文字
            btn.classList.remove('bg-amber-500', 'text-white');
            btn.classList.add('bg-white', 'text-amber-700', 'border', 'border-amber-200', 'hover:bg-amber-50');
        }
    });
}


function addToCart(productId) {
    const product = products.find(p => p.id === productId);
    const existingItem = cart.find(item => item.id === productId);

    if (existingItem) {
        existingItem.quantity++;
    } else {
        cart.push({ ...product, quantity: 1 });
    }

    updateCart();
    renderProducts(products);
}

function removeFromCart(productId) {
    cart = cart.filter(item => item.id !== productId);
    updateCart();
    renderProducts(products);
}

function updateCartQuantity(productId, delta) {
    const item = cart.find(item => item.id === productId);
    if (item) {
        item.quantity += delta;
        if (item.quantity <= 0) {
            removeFromCart(productId);
        } else {
            updateCart();
            renderProducts(products);
        }
    }
}

function updateCart() {
    const container = document.getElementById('cartItems');

    if (cart.length === 0) {
        container.innerHTML = `
                    <div class="text-center text-amber-400 py-8">
                        <i class="fas fa-coffee text-4xl mb-2"></i>
                        <p>请选择商品</p>
                    </div>
                `;
    } else {
        container.innerHTML = cart.map(item => `
                    <div class="cart-item flex items-center justify-between p-3 rounded-lg mb-2">
                        <div class="flex items-center gap-3">
                            <div class="w-12 h-12 rounded-lg bg-amber-100 flex items-center justify-center text-2xl">
                                ${getProductIcon(item.category)}
                            </div>
                            <div>
                                <h4 class="font-medium text-amber-900 text-sm">${item.name}</h4>
                                <p class="text-xs text-amber-600">¥${item.sale_price}</p>
                            </div>
                        </div>
                        <div class="flex items-center gap-3">
                            <button onclick="updateCartQuantity(${item.id}, -1)" class="w-6 h-6 rounded-full bg-amber-200 text-amber-700 flex items-center justify-center hover:bg-amber-300">
                                <i class="fas fa-minus text-xs"></i>
                            </button>
                            <span class="font-medium text-amber-900 w-6 text-center">${item.quantity}</span>
                            <button onclick="updateCartQuantity(${item.id}, 1)" class="w-6 h-6 rounded-full bg-amber-500 text-white flex items-center justify-center hover:bg-amber-600">
                                <i class="fas fa-plus text-xs"></i>
                            </button>
                        </div>
                    </div>
                `).join('');
    }

    calculateCartTotal();
}

async function queryMember() {
    const phone = document.getElementById('posMemberPhone').value;

    if(!phone || phone.length !== 11) {
        showToast('请输入正确的11位手机号', 'error');
        return;
    }

    try {
        // 使用 fetchWithToken
        const response = await fetchWithToken(`${API_BASE_URL}/api/member?phone=${phone}`);

        if(!response) return; // 401 被拦截

        if(response.status === 404) {
            showToast('会员不存在', 'error');
            clearMember();
            return;
        }

        const result = await response.json();
        if(!result.success) {
            throw new Error(result.msg);
        }

        const member = result.data;  // 注意：是 result.data

        // 保存当前会员
        currentMember = member;

        // 显示会员信息
        const infoDiv = document.getElementById('posMemberInfo');
        infoDiv.innerHTML = `
                <div class="flex justify-between items-center">
                    <div>
                        <p class="font-medium text-amber-900">${member.name || member.phone}</p>
                        <p class="text-xs text-amber-600">
                            ${getMemberLevelText(member.level)} |
                            余额: ¥${member.balance.toFixed(2)} |
                            积分: ${member.points}
                        </p>
                    </div>
                    <button onclick="clearMember()" class="text-red-500 hover:text-red-700">
                        <i class="fas fa-times"></i>
                    </button>
                </div>
            `;
        infoDiv.classList.remove('hidden');

        // 重新计算购物车优惠
        calculateCartTotal();

        showToast('会员查询成功', 'success');

    } catch(error) {
        console.error('查询会员失败:', error);
        showToast('查询失败: ' + error.message, 'error');
    }
}

function clearMember() {
    currentMember = null;
    document.getElementById('posMemberInfo').classList.add('hidden');
    document.getElementById('posMemberPhone').value = '';
    calculateCartTotal();
}

function setPayType(type) {
    currentPayType = type;
    document.querySelectorAll('.pay-type-btn').forEach(btn => {
        if (parseInt(btn.dataset.type) === type) {
            btn.classList.add('bg-amber-500', 'text-white', 'border-amber-500');
            btn.classList.remove('text-amber-700', 'border-amber-200');
        } else {
            btn.classList.remove('bg-amber-500', 'text-white', 'border-amber-500');
            btn.classList.add('text-amber-700', 'border-amber-200');
        }
    });
}

function calculateCartTotal() {
    const subtotal = cart.reduce((sum, item) => sum + item.sale_price * item.quantity, 0);
    let discount = 0;

    // 会员折扣逻辑
    if (currentMember) {
        const discountRates = { 1: 0.95, 2: 0.9, 3: 0.85 };
        discount = subtotal * (1 - (discountRates[currentMember.level] || 1));
    }

    const total = subtotal - discount;

    document.getElementById('cartSubtotal').textContent = `¥${subtotal.toFixed(2)}`;
    document.getElementById('cartDiscount').textContent = `-¥${discount.toFixed(2)}`;
    document.getElementById('cartTotal').textContent = `¥${total.toFixed(2)}`;
}

async function submitOrder() {
    if (cart.length === 0) {
        showToast('购物车为空', 'error');
        return;
    }
    if (!currentPayType) {
        showToast('请选择支付方式', 'error');
        return;
    }

    const subtotal = cart.reduce((sum, item) => sum + item.sale_price * item.quantity, 0);
    const discount = parseFloat(document.getElementById('cartDiscount').textContent.replace('-¥', ''));
    const payAmount = parseFloat(document.getElementById('cartTotal').textContent.replace('¥', ''));

    const orderData = {
        member_id: currentMember?.id || null,
        total_amount: subtotal,
        discount_amount: discount,
        pay_amount: payAmount,
        pay_type: currentPayType,
        items: cart.map(item => ({
            product_id: item.id,
            product_name: item.name,
            quantity: item.quantity,
            unit_price: item.sale_price,
            subtotal: item.sale_price * item.quantity
        }))
    };

    try {
        // API: POST /api/sale_order
        const response = await fetchWithToken(`${API_BASE_URL}/api/sale_order`, {
            method: 'POST',
            body: JSON.stringify(orderData)
        });
        if (!response) return;

        const result = await response.json();
        if (!result.success) {
            throw new Error(result.msg || '下单失败');
        }

        showToast('订单提交成功', 'success');

        cart = [];
        currentMember = null;
        currentPayType = null;
        document.getElementById('posMemberInfo').classList.add('hidden');
        document.getElementById('posMemberPhone').value = '';
        document.querySelectorAll('.pay-type-btn').forEach(btn => {
            btn.classList.remove('bg-amber-500', 'text-white', 'border-amber-500');
            btn.classList.add('text-amber-700', 'border-amber-200');
        });
        updateCart();
        renderProducts(products);
    } catch (error) {
        console.error('提交订单失败:', error);
        showToast('下单失败: ' + (error.message || '未知错误'), 'error');
    }
}

// ==========================================
// 订单管理功能
// ==========================================
async function loadOrdersData(page = 1,signal = null) {
    try {
        currentPage = page;

        // 使用 fetchWithToken
        const response = await fetchWithToken(
            `${API_BASE_URL}/api/sale_order/list?page=${page}&size=${pageSize}&dateType=${currentOrderFilter}`
            , { signal }
        );
        if(!response) return;
        if (document.getElementById('orders').classList.contains('hidden')) return;

        const result = await response.json();
        if(!result.success) {
            throw new Error(result.msg);
        }

        const data = result.data;  // {total, list, page, size, pages}

        // 渲染表格
        const tbody = document.getElementById('ordersTableBody');
        if(data.list.length === 0) {
            tbody.innerHTML = '<tr><td colspan="10" class="text-center text-amber-400 py-8">暂无订单</td></tr>';
        } else {
            tbody.innerHTML = data.list.map(order => `
                    <tr>
                        <td class="py-4 px-6 font-mono text-sm text-amber-700">${order.order_no}</td>
                        <td class="py-4 px-6 text-sm text-gray-600">${order.created_at}</td>
                        <td class="py-4 px-6 text-sm">${order.member_name}</td>
                        <td class="py-4 px-6 text-sm text-gray-600">${order.items_count}件商品</td>
                        <td class="py-4 px-6 font-medium text-amber-900">¥${order.total_amount.toFixed(2)}</td>
                        <td class="py-4 px-6 text-sm text-red-500">-¥${order.discount_amount.toFixed(2)}</td>
                        <td class="py-4 px-6 font-bold text-amber-600">¥${order.pay_amount.toFixed(2)}</td>
                        <td class="py-4 px-6 text-sm">${getPayTypeText(order.pay_type)}</td>
                        <td class="py-4 px-6 text-sm">${order.cashier}</td>
                        <td class="py-4 px-6">
                            <button onclick="viewOrderDetail('${order.order_no}')" class="text-amber-600 hover:text-amber-800 text-sm font-medium mr-2">详情</button>
                            ${order.status === 1 ? `<button onclick="cancelOrder('${order.order_no}')" class="text-red-500 hover:text-red-700 text-sm font-medium">取消</button>` : ''}
                        </td>
                    </tr>
                `).join('');
        }

        // 更新分页信息
        document.getElementById('ordersTotal').textContent = data.total;
        document.getElementById('pageInfo').textContent = `${data.page} / ${data.pages}`;

        // 禁用/启用分页按钮
        const prevBtn = document.querySelector('button[onclick="changePage(-1)"]');
        const nextBtn = document.querySelector('button[onclick="changePage(1)"]');

        if(prevBtn) prevBtn.disabled = data.page <= 1;
        if(nextBtn) nextBtn.disabled = data.page >= data.pages;

    } catch(error) {
        if (error.name === 'AbortError') return;
        console.error('加载订单列表失败:', error);
        document.getElementById('ordersTableBody').innerHTML =
            '<tr><td colspan="10" class="text-center text-red-400 py-8">加载失败</td></tr>';
    }
}

async function viewOrderDetail(orderNo) {
    try {
        // 使用 fetchWithToken
        const response = await fetchWithToken(`${API_BASE_URL}/api/sale_order/${orderNo}/detail`);
        if(!response) return;

        const result = await response.json();
        if(!result.success) {
            throw new Error(result.msg);
        }

        const order = result.data;

        // 填充模态框内容
        const content = document.getElementById('orderDetailContent');
        content.innerHTML = `
                <div class="space-y-4">
                    <div class="flex justify-between items-center p-4 bg-amber-50 rounded-lg">
                        <div>
                            <p class="text-sm text-amber-600">订单号</p>
                            <p class="font-mono font-bold text-amber-900">${order.order_no}</p>
                        </div>
                        <span class="status-badge ${getStatusClass(order.status)}">${getStatusText(order.status)}</span>
                    </div>

                    <div class="space-y-2">
                        <h4 class="font-medium text-amber-900">商品明细</h4>
                        ${order.items.map(item => `
                            <div class="flex justify-between items-center p-3 border border-amber-100 rounded-lg">
                                <div>
                                    <p class="font-medium text-amber-900">${item.product_name}</p>
                                    <p class="text-sm text-amber-600">¥${item.unit_price.toFixed(2)} x ${item.quantity}</p>
                                </div>
                                <span class="font-bold text-amber-900">¥${item.subtotal.toFixed(2)}</span>
                            </div>
                        `).join('')}
                    </div>

                    <div class="border-t border-amber-200 pt-4 space-y-2">
                        <div class="flex justify-between text-sm">
                            <span class="text-amber-700">商品总额</span>
                            <span class="font-medium">¥${order.total_amount.toFixed(2)}</span>
                        </div>
                        <div class="flex justify-between text-sm">
                            <span class="text-amber-700">优惠金额</span>
                            <span class="font-medium text-red-500">-¥${order.discount_amount.toFixed(2)}</span>
                        </div>
                        <div class="flex justify-between text-lg font-bold">
                            <span class="text-amber-900">实付金额</span>
                            <span class="text-amber-600">¥${order.pay_amount.toFixed(2)}</span>
                        </div>
                    </div>

                    <div class="grid grid-cols-2 gap-4 text-sm">
                        <div>
                            <span class="text-amber-600">支付方式:</span>
                            <span class="ml-2 text-amber-900">${getPayTypeText(order.pay_type)}</span>
                        </div>
                        <div>
                            <span class="text-amber-600">收银员:</span>
                            <span class="ml-2 text-amber-900">${order.cashier}</span>
                        </div>
                        <div>
                            <span class="text-amber-600">下单时间:</span>
                            <span class="ml-2 text-amber-900">${order.created_at}</span>
                        </div>
                        <div>
                            <span class="text-amber-600">会员:</span>
                            <span class="ml-2 text-amber-900">${order.member_name} ${order.member_level ? '(VIP' + order.member_level + ')' : ''}</span>
                        </div>
                    </div>
                </div>
            `;

        // 显示模态框
        document.getElementById('orderDetailModal').classList.remove('hidden');
        setTimeout(() => {
            document.getElementById('orderDetailModalContent').classList.remove('scale-95', 'opacity-0');
            document.getElementById('orderDetailModalContent').classList.add('scale-100', 'opacity-100');
        }, 10);

    } catch(error) {
        console.error('加载订单详情失败:', error);
        showToast('加载订单详情失败: ' + error.message, 'error');
    }
}

function closeOrderDetailModal() {
    const content = document.getElementById('orderDetailModalContent');
    content.classList.remove('scale-100', 'opacity-100');
    content.classList.add('scale-95', 'opacity-0');
    setTimeout(() => {
        document.getElementById('orderDetailModal').classList.add('hidden');
    }, 300);
}

// ==========================================
// 会员管理功能
// ==========================================
async function loadMembersData(signal = null) {
    try {
        const response = await fetchWithToken(`${API_BASE_URL}/api/member/list`, { signal });
        if(!response) return;
        if (document.getElementById('members').classList.contains('hidden')) return;

        const result = await response.json();
        if(!result.success) {
            throw new Error(result.msg);
        }

        allMembers = result.data;  // 保存全部数据
        filteredMembers = allMembers;  // 初始显示全部

        renderMembersTable(allMembers);

    } catch(error) {
        if (error.name === 'AbortError') return;
        console.error('加载会员列表失败:', error);
        document.getElementById('membersTableBody').innerHTML =
            '<tr><td colspan="10" class="text-center text-red-400 py-8">加载失败</td></tr>';
    }
}

function renderMembersTable(members) {
    const tbody = document.getElementById('membersTableBody');

    if(members.length === 0) {
        tbody.innerHTML = '<tr><td colspan="10" class="text-center text-amber-400 py-8">暂无会员</td></tr>';
        return;
    }

    tbody.innerHTML = members.map(member => `
            <tr>
                <td class="py-4 px-6 text-sm text-amber-700">#${member.id}</td>
                <td class="py-4 px-6 font-mono text-sm">${member.phone}</td>
                <td class="py-4 px-6 text-sm">${member.name || '-'}</td>
                <td class="py-4 px-6">
                    <span class="status-badge member-level-${member.level}">${getMemberLevelText(member.level)}</span>
                </td>
                <td class="py-4 px-6 font-medium text-amber-900">¥${member.balance.toFixed(2)}</td>
                <td class="py-4 px-6 text-sm">${member.points}</td>
                <td class="py-4 px-6 text-sm">¥${member.total_consume.toFixed(2)}</td>
                <td class="py-4 px-6">
                    <span class="status-badge ${member.status === 1 ? 'status-success' : 'status-danger'}">
                        ${member.status === 1 ? '正常' : '冻结'}
                    </span>
                </td>
                <td class="py-4 px-6 text-sm text-gray-500">${member.created_at}</td>
                <td class="py-4 px-6">
                    <button onclick="rechargeMember(${member.id})" class="text-amber-600 hover:text-amber-800 text-sm font-medium mr-2">充值</button>
                    <button onclick="viewMemberDetail(${member.id})" class="text-blue-600 hover:text-blue-800 text-sm font-medium">详情</button>
                </td>
            </tr>
        `).join('');
}
function searchMembers() {
    const keyword = document.getElementById('memberSearch').value.trim().toLowerCase();

    if(!keyword) {
        filteredMembers = allMembers;
    } else {
        filteredMembers = allMembers.filter(member => {
            // 支持手机号、姓名搜索
            const phoneMatch = member.phone.includes(keyword);
            const nameMatch = member.name && member.name.toLowerCase().includes(keyword);
            return phoneMatch || nameMatch;
        });
    }

    renderMembersTable(filteredMembers);
}

async function submitNewMember() {
    const phone = document.getElementById('newMemberPhone').value;
    const name = document.getElementById('newMemberName').value;
    const level = parseInt(document.getElementById('newMemberLevel').value);
    const balance = parseFloat(document.getElementById('newMemberBalance').value) || 0;

    if (!phone || phone.length !== 11) {
        showToast('请输入正确的手机号', 'error');
        return;
    }

    try {
        // API: POST /api/member
        const response = await fetchWithToken(`${API_BASE_URL}/api/member`, {
            method: 'POST',
            body: JSON.stringify({ phone, name, level, balance })
        });
        if(!response) return;

        const result = await response.json();
        if(!result.success) throw new Error(result.msg);

        showToast('会员添加成功', 'success');
        closeMemberModal();
        loadMembersData();
    } catch (error) {
        console.error('添加会员失败:', error);
        showToast('添加失败: ' + (error.message || ''), 'error');
    }
}

// ==========================================
// 库存管理功能
// ==========================================
// async function loadInventoryData(signal = null) {
//     try {
//         // API: GET /api/material/list
//         const response = await fetchWithToken(`${API_BASE_URL}/api/material/list`, { signal });
//         if (document.getElementById('inventory').classList.contains('hidden')) return;
//
//         materials = await response.json();
//
//         const tbody = document.getElementById('inventoryTableBody');
//         tbody.innerHTML = materials.map(material => {
//             const isLow = material.stock_quantity < material.safety_stock;
//             return `
//                         <tr class="${isLow ? 'bg-red-50' : ''}">
//                             <td class="py-4 px-6 text-sm text-amber-700">#${material.id}</td>
//                             <td class="py-4 px-6 font-medium text-amber-900">${material.name}</td>
//                             <td class="py-4 px-6 text-sm">${material.unit}</td>
//                             <td class="py-4 px-6 ${isLow ? 'text-red-600 font-bold' : 'text-amber-900'}">${material.stock_quantity}</td>
//                             <td class="py-4 px-6 text-sm text-gray-600">${material.safety_stock}</td>
//                             <td class="py-4 px-6"><span class="status-badge ${material.status === 1 ? 'status-success' : 'status-danger'}">${material.status === 1 ? '启用' : '停用'}</span></td>
//                             <td class="py-4 px-6 text-sm text-gray-500">${material.updated_at}</td>
//                             <td class="py-4 px-6">
//                                 <button onclick="adjustStock(${material.id})" class="text-amber-600 hover:text-amber-800 text-sm font-medium">调整</button>
//                             </td>
//                         </tr>
//                     `;
//         }).join('');
//
//         loadInventoryLogs();
//     } catch (error) {
//         if (error.name === 'AbortError') return;
//         console.error('加载库存失败:', error);
//     }
// }

async function loadInventoryLogs() {
    try {
        // API: GET /api/inventory_log/recent?limit=10
        const response = await fetch(`${API_BASE_URL}/api/inventory_log/recent?limit=10`);
        const data = await response.json();

        const container = document.getElementById('inventoryLogs');
        container.innerHTML = data.map(log => `
                    <div class="flex items-center justify-between p-3 bg-amber-50 rounded-lg text-sm">
                        <div>
                            <p class="font-medium text-amber-900">${log.material_name}</p>
                            <p class="text-xs text-amber-600">${log.type_name} | ${log.created_at}</p>
                        </div>
                        <div class="text-right">
                            <p class="font-bold ${log.quantity > 0 ? 'text-green-600' : 'text-red-600'}">${log.quantity > 0 ? '+' : ''}${log.quantity}</p>
                            <p class="text-xs text-gray-500">余: ${log.after_stock}</p>
                        </div>
                    </div>
                `).join('');
    } catch (error) {
        console.error('加载库存流水失败:', error);
    }
}

// ==========================================
// 采购管理功能
// ==========================================
async function loadPurchaseData(signal = null) {
    try {
        const response = await fetchWithToken(`${API_BASE_URL}/api/purchase_order/list`, { signal });
        if(!response) return;
        if (document.getElementById('purchase').classList.contains('hidden')) return;

        const result = await response.json();
        if(!result.success) {
            throw new Error(result.msg);
        }

        const orders = result.data;
        const tbody = document.getElementById('purchaseTableBody');

        if(orders.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center text-amber-400 py-8">暂无采购单</td></tr>';
            return;
        }

        tbody.innerHTML = orders.map(order => `
                <tr>
                    <td class="py-4 px-6 font-mono text-sm text-amber-700">${order.order_no}</td>
                    <td class="py-4 px-6 text-sm">${order.supplier || '-'}</td>
                    <td class="py-4 px-6 font-medium text-amber-900">¥${order.total_amount.toFixed(2)}</td>
                    <td class="py-4 px-6">
                        <span class="status-badge ${getPurchaseStatusClass(order.status)}">
                            ${getPurchaseStatusText(order.status)}
                        </span>
                    </td>
                    <td class="py-4 px-6 text-sm">${order.operator}</td>
                    <td class="py-4 px-6 text-sm text-gray-500">${order.created_at}</td>
                    <td class="py-4 px-6">
                        ${order.status === 0 ? `
                            <button onclick="confirmInbound(${order.id})" class="text-green-600 hover:text-green-800 text-sm font-medium mr-2">确认入库</button>
                        ` : ''}
                        <button onclick="viewPurchaseDetail(${order.id})" class="text-amber-600 hover:text-amber-800 text-sm font-medium">详情</button>
                    </td>
                </tr>
            `).join('');

    } catch(error) {
        if (error.name === 'AbortError') return;
        console.error('加载采购单失败:', error);
        document.getElementById('purchaseTableBody').innerHTML =
            '<tr><td colspan="7" class="text-center text-red-400 py-8">加载失败</td></tr>';
    }
}

async function submitPurchase() {
    const supplier = document.getElementById('purchaseSupplier').value;
    const date = document.getElementById('purchaseDate').value;

    if (!supplier) {
        showToast('请输入供应商', 'error');
        return;
    }

    const items = [];
    let manualUnitMissing = false;
    document.querySelectorAll('.purchase-item').forEach(item => {
        const materialId = item.querySelector('.purchase-material').value;
        const materialNameInput = item.querySelector('.purchase-material-name');
        const materialName = materialNameInput ? (materialNameInput.value || '').trim() : '';
        const materialUnitInput = item.querySelector('.purchase-material-unit');
        const materialUnit = materialUnitInput ? (materialUnitInput.value || '').trim() : '';
        const qty = parseFloat(item.querySelector('.purchase-qty').value);
        const price = parseFloat(item.querySelector('.purchase-price').value);

        if (!(qty > 0) || !(price >= 0)) return;
        if (materialId) {
            items.push({
                material_id: parseInt(materialId, 10),
                quantity: qty,
                unit_price: price,
                subtotal: qty * price
            });
        } else if (materialName) {
            if (!materialUnit) {
                manualUnitMissing = true;
                return;
            }
            items.push({
                material_name: materialName,
                material_unit: materialUnit,
                quantity: qty,
                unit_price: price,
                subtotal: qty * price
            });
        }
    });

    if (manualUnitMissing) {
        showToast('手动输入原料名称时，请同时填写单位', 'error');
        return;
    }

    if (items.length === 0) {
        showToast('请添加采购原料', 'error');
        return;
    }

    const totalAmount = items.reduce((sum, item) => sum + item.subtotal, 0);

    try {
        // API: POST /api/purchase_order
        const response = await fetchWithToken(`${API_BASE_URL}/api/purchase_order`, {
            method: 'POST',
            body: JSON.stringify({
                supplier,
                total_amount: totalAmount,
                items
            })
        });
        if (!response) return;
        const result = await response.json();
        if (!result.success) throw new Error(result.msg || '创建失败');

        showToast('采购单创建成功', 'success');
        closePurchaseModal();
        loadPurchaseData();
    } catch (error) {
        console.error('创建采购单失败:', error);
        showToast('创建失败: ' + (error.message || ''), 'error');
    }
}

// ==========================================
// 报表功能
// ==========================================
async function loadReportsData(signal = null) {
    try {
        // 加载销售日报
        const dailyRes = await fetchWithToken(`${API_BASE_URL}/api/v_daily_sales?limit=30`, { signal });
        if(!dailyRes) return;
        if (document.getElementById('reports').classList.contains('hidden')) return;

        const dailyResult = await dailyRes.json();
        if(!dailyResult.success) throw new Error(dailyResult.msg);

        renderDailyReport(dailyResult.data);

        // 加载商品排行 - ✅ 添加 { signal }
        const rankRes = await fetchWithToken(`${API_BASE_URL}/api/v_product_rank?limit=10`, { signal });
        if(!rankRes) return;

        const rankResult = await rankRes.json();
        if(!rankResult.success) throw new Error(rankResult.msg);

        renderProductRank(rankResult.data);

    } catch(error) {
        if (error.name === 'AbortError') return;
        console.error('加载报表失败:', error);
        showToast('加载报表失败: ' + (error.message || ''), 'error');
    }
}

// 渲染商品排行
function renderProductRank(products) {
    const container = document.getElementById('productRankList');

    if(products.length === 0) {
        container.innerHTML = '<div class="text-center text-amber-400 py-4">暂无数据</div>';
        return;
    }

    container.innerHTML = products.map((product, index) => `
            <div class="flex items-center justify-between p-3 ${index < 3 ? 'bg-amber-50' : 'bg-gray-50'} rounded-lg">
                <div class="flex items-center gap-3">
                    <span class="w-8 h-8 rounded-full ${index < 3 ? 'bg-amber-500 text-white' : 'bg-gray-300 text-gray-600'} flex items-center justify-center font-bold text-sm">
                        ${index + 1}
                    </span>
                    <div>
                        <p class="font-medium text-amber-900">${product.name}</p>
                        <p class="text-xs text-amber-600">${product.category} | ${product.order_count}单</p>
                    </div>
                </div>
                <div class="text-right">
                    <p class="font-bold text-amber-900">${product.total_sold}</p>
                    <p class="text-xs text-amber-600">销量</p>
                </div>
            </div>
        `).join('');
}

// 渲染销售日报
function renderDailyReport(data) {
    const tbody = document.getElementById('dailyReportBody');

    if(data.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" class="text-center text-amber-400 py-8">暂无数据</td></tr>';
        return;
    }

    tbody.innerHTML = data.map(day => `
            <tr>
                <td class="py-3 px-4 text-sm">${day.sale_date}</td>
                <td class="py-3 px-4 text-sm">${day.order_count}</td>
                <td class="py-3 px-4 font-medium">¥${day.total_amount.toFixed(2)}</td>
                <td class="py-3 px-4 text-sm text-red-500">-¥${day.discount_amount.toFixed(2)}</td>
                <td class="py-3 px-4 font-bold text-amber-600">¥${day.real_income.toFixed(2)}</td>
                <td class="py-3 px-4 text-sm">¥${day.cash_amount.toFixed(2)}</td>
                <td class="py-3 px-4 text-sm">¥${day.mobile_amount.toFixed(2)}</td>
            </tr>
        `).join('');
}

// ==========================================
// 工具函数
// ==========================================
function getStatusClass(status) {
    return status === 1 ? 'status-success' : 'status-danger';
}

function getStatusText(status) {
    return status === 1 ? '已完成' : '已取消';
}

function getPurchaseStatusClass(status) {
    // 0:待入库, 1:已入库, 2:已取消
    const classes = ['status-warning', 'status-success', 'status-danger'];
    return classes[status] || 'status-info';
}

function getPurchaseStatusText(status) {
    const texts = ['待入库', '已入库', '已取消'];
    return texts[status] || '未知';
}

// 辅助函数
function getPayTypeText(type) {
    return ['', '现金', '微信', '支付宝', '余额'][type] || '未知';
}

function getMemberLevelText(level) {
    return ['', '普通会员', '银卡会员', '金卡会员'][level] || '普通会员';
}

function showToast(message, type = 'info') {
    const toast = document.createElement('div');
    const colors = {
        success: 'bg-green-500',
        error: 'bg-red-500',
        warning: 'bg-yellow-500',
        info: 'bg-blue-500'
    };

    toast.className = `fixed top-4 right-4 ${colors[type]} text-white px-6 py-3 rounded-lg shadow-lg z-50 transform translate-x-full transition-transform duration-300 flex items-center gap-2`;
    toast.innerHTML = `
                <i class="fas ${type === 'success' ? 'fa-check-circle' : type === 'error' ? 'fa-exclamation-circle' : 'fa-info-circle'}"></i>
                <span>${message}</span>
            `;

    document.body.appendChild(toast);
    setTimeout(() => toast.classList.remove('translate-x-full'), 100);
    setTimeout(() => {
        toast.classList.add('translate-x-full');
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

// ==========================================
// 模态框控制
// ==========================================
function openMemberModal() {
    document.getElementById('memberModal').classList.remove('hidden');
    setTimeout(() => {
        document.getElementById('memberModalContent').classList.remove('scale-95', 'opacity-0');
        document.getElementById('memberModalContent').classList.add('scale-100', 'opacity-100');
    }, 10);
}

function closeMemberModal() {
    const content = document.getElementById('memberModalContent');
    content.classList.remove('scale-100', 'opacity-100');
    content.classList.add('scale-95', 'opacity-0');
    setTimeout(() => {
        document.getElementById('memberModal').classList.add('hidden');
        document.getElementById('newMemberPhone').value = '';
        document.getElementById('newMemberName').value = '';
        document.getElementById('newMemberBalance').value = '';
    }, 300);
}

function openProductModal() {
    document.getElementById('productModal').classList.remove('hidden');
    setTimeout(() => {
        document.getElementById('productModalContent').classList.remove('scale-95', 'opacity-0');
        document.getElementById('productModalContent').classList.add('scale-100', 'opacity-100');
    }, 10);
}

function closeProductModal() {
    const content = document.getElementById('productModalContent');
    content.classList.remove('scale-100', 'opacity-100');
    content.classList.add('scale-95', 'opacity-0');
    setTimeout(() => {
        document.getElementById('productModal').classList.add('hidden');
        document.getElementById('newProductName').value = '';
        document.getElementById('newProductTag').value = '';
        document.getElementById('newProductPrice').value = '';
    }, 300);
}

async function submitNewProduct() {
    const name = (document.getElementById('newProductName').value || '').trim();
    const category = document.getElementById('newProductCategory').value;
    const productTag = (document.getElementById('newProductTag').value || '').trim() || '常规商品';
    const salePrice = parseFloat(document.getElementById('newProductPrice').value || '0');
    if (!name) {
        showToast('商品名称不能为空', 'error');
        return;
    }
    if (isNaN(salePrice) || salePrice < 0) {
        showToast('请输入正确的售价', 'error');
        return;
    }
    try {
        const response = await fetchWithToken(`${API_BASE_URL}/api/product`, {
            method: 'POST',
            body: JSON.stringify({
                name,
                category,
                product_tag: productTag,
                sale_price: salePrice,
                status: 0
            })
        });
        if (!response) return;
        const result = await response.json();
        if (!result.success) throw new Error(result.msg || '新增失败');

        showToast('商品添加成功，默认下架，请在商品管理中「配置配方」后再上架', 'success');
        closeProductModal();
        loadPosData();
        if (!document.getElementById('productManage').classList.contains('hidden')) {
            loadProductManageData();
        }
    } catch (error) {
        console.error('新增商品失败:', error);
        showToast('新增商品失败: ' + (error.message || ''), 'error');
    }
}

async function deleteProduct(productId, productName) {
    if (!confirm(`确认删除商品「${productName}」吗？\n删除后将从在售列表移除。`)) return;
    try {
        const response = await fetchWithToken(`${API_BASE_URL}/api/product/${productId}`, {
            method: 'DELETE'
        });
        if (!response) return;
        const result = await response.json();
        if (!result.success) throw new Error(result.msg || '删除失败');
        showToast('商品已删除', 'success');
        loadPosData();
        if (!document.getElementById('productManage').classList.contains('hidden')) {
            loadProductManageData();
        }
    } catch (error) {
        console.error('删除商品失败:', error);
        showToast('删除商品失败: ' + (error.message || ''), 'error');
    }
}

async function loadProductManageData(signal = null) {
    try {
        const response = await fetchWithToken(`${API_BASE_URL}/api/product/list`, { signal });
        if (!response) return;
        if (document.getElementById('productManage').classList.contains('hidden')) return;
        const result = await response.json();
        if (!result.success) throw new Error(result.msg || '加载失败');

        const list = result.data || [];
        const tbody = document.getElementById('productManageTableBody');
        if (list.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" class="text-center text-amber-400 py-8">暂无商品</td></tr>';
            return;
        }
        tbody.innerHTML = list.map(p => `
            <tr>
                <td class="py-3 px-6 text-sm text-amber-700">#${p.id}</td>
                <td class="py-3 px-6 text-sm">${p.name || '-'}</td>
                <td class="py-3 px-6 text-sm">${p.category || '-'}</td>
                <td class="py-3 px-6 text-sm">${p.product_tag || '常规商品'}</td>
                <td class="py-3 px-6 text-sm">¥${Number(p.sale_price || 0).toFixed(2)}</td>
                <td class="py-3 px-6">
                    <span class="status-badge ${p.status === 1 ? 'status-success' : 'status-danger'}">${p.status === 1 ? '上架' : '下架'}</span>
                </td>
                <td class="py-3 px-6">
                    <button type="button" onclick="openProductRecipeModal(${p.id}, '${(p.name || '').replace(/\\/g, '\\\\').replace(/'/g, "\\'")}')" class="text-blue-600 hover:text-blue-800 text-sm font-medium mr-3">配置配方</button>
                    ${p.status === 1
                        ? `<button onclick="updateProductStatus(${p.id},0)" class="text-amber-600 hover:text-amber-800 text-sm font-medium mr-3">下架</button>`
                        : `<button onclick="updateProductStatus(${p.id},1)" class="text-green-600 hover:text-green-800 text-sm font-medium mr-3">上架</button>`
                    }
                    <button onclick="deleteProduct(${p.id}, '${(p.name || '').replace(/'/g, "\\'")}')" class="text-red-500 hover:text-red-700 text-sm font-medium">删除</button>
                </td>
            </tr>
        `).join('');
    } catch (error) {
        if (error.name === 'AbortError') return;
        console.error('加载商品管理失败:', error);
        const tbody = document.getElementById('productManageTableBody');
        if (tbody) tbody.innerHTML = '<tr><td colspan="7" class="text-center text-red-400 py-8">加载失败</td></tr>';
    }
}

async function updateProductStatus(productId, status) {
    try {
        const actionText = status === 1 ? '上架' : '下架';
        if (!confirm(`确认${actionText}该商品吗？`)) return;
        const response = await fetchWithToken(`${API_BASE_URL}/api/product/${productId}/status?status=${status}`, {
            method: 'PUT'
        });
        if (!response) return;
        const result = await response.json();
        if (!result.success) throw new Error(result.msg || '操作失败');
        showToast(`商品已${actionText}`, 'success');
        loadProductManageData();
        loadPosData();
    } catch (error) {
        console.error('更新商品状态失败:', error);
        showToast('操作失败: ' + (error.message || ''), 'error');
    }
}

let productRecipeMaterialOptionsHtml = '';

function openProductRecipeModal(productId, productName) {
    document.getElementById('productRecipeProductId').value = String(productId);
    document.getElementById('productRecipeTitleName').textContent = productName || ('#' + productId);
    document.getElementById('productRecipeModal').classList.remove('hidden');
    setTimeout(() => {
        document.getElementById('productRecipeModalContent').classList.remove('scale-95', 'opacity-0');
        document.getElementById('productRecipeModalContent').classList.add('scale-100', 'opacity-100');
    }, 10);
    loadProductRecipeEditor(productId);
}

function closeProductRecipeModal() {
    const content = document.getElementById('productRecipeModalContent');
    content.classList.remove('scale-100', 'opacity-100');
    content.classList.add('scale-95', 'opacity-0');
    setTimeout(() => {
        document.getElementById('productRecipeModal').classList.add('hidden');
        document.getElementById('productRecipeRows').innerHTML = '';
        document.getElementById('productRecipeProductId').value = '';
    }, 300);
}

async function loadProductRecipeEditor(productId) {
    const rowsEl = document.getElementById('productRecipeRows');
    rowsEl.innerHTML = '<div class="text-sm text-amber-600 py-2">加载中…</div>';
    try {
        const matRes = await fetchWithToken(`${API_BASE_URL}/api/material/list`);
        if (!matRes) return;
        const matJson = await matRes.json();
        if (!matJson.success) throw new Error(matJson.msg || '加载原料失败');
        const materials = matJson.data || [];
        productRecipeMaterialOptionsHtml = '<option value="">选择原料</option>' +
            materials.map(m => `<option value="${m.id}">${m.name} (${m.unit})</option>`).join('');

        const recRes = await fetchWithToken(`${API_BASE_URL}/api/product/${productId}/recipe`);
        if (!recRes) return;
        const recJson = await recRes.json();
        if (!recJson.success) throw new Error(recJson.msg || '加载配方失败');
        const lines = recJson.data || [];
        rowsEl.innerHTML = '';
        if (lines.length === 0) {
            addProductRecipeRow();
        } else {
            lines.forEach(line => addProductRecipeRow(line.material_id, line.consume_qty));
        }
    } catch (e) {
        console.error(e);
        rowsEl.innerHTML = '<div class="text-sm text-red-500 py-2">加载失败</div>';
        showToast(e.message || '加载失败', 'error');
    }
}

function addProductRecipeRow(materialId, consumeQty) {
    const rowsEl = document.getElementById('productRecipeRows');
    const row = document.createElement('div');
    row.className = 'flex gap-2 items-center recipe-row flex-wrap';
    const mid = materialId != null && materialId !== '' ? String(materialId) : '';
    const qty = consumeQty != null && consumeQty !== '' ? String(consumeQty) : '';
    row.innerHTML = `
        <select aria-label="原料" class="recipe-material flex-1 min-w-[140px] px-3 py-2 rounded-lg border border-amber-200 text-sm focus:outline-none focus:border-amber-500">${productRecipeMaterialOptionsHtml || '<option value="">选择原料</option>'}</select>
        <input type="number" aria-label="每份消耗" class="recipe-consume w-28 px-3 py-2 rounded-lg border border-amber-200 text-sm focus:outline-none focus:border-amber-500" placeholder="消耗量" min="0.001" step="0.001" value="${qty}">
        <button type="button" onclick="removeProductRecipeRow(this)" title="删除此行" class="text-red-500 hover:text-red-700 p-2"><i class="fas fa-trash"></i></button>
    `;
    rowsEl.appendChild(row);
    const sel = row.querySelector('.recipe-material');
    if (sel && mid) sel.value = mid;
}

function removeProductRecipeRow(btn) {
    const row = btn.closest('.recipe-row');
    if (row) row.remove();
    const rowsEl = document.getElementById('productRecipeRows');
    if (rowsEl && rowsEl.querySelectorAll('.recipe-row').length === 0) {
        addProductRecipeRow();
    }
}

async function saveProductRecipe() {
    const productId = document.getElementById('productRecipeProductId').value;
    if (!productId) return;
    const items = [];
    const seen = new Set();
    for (const row of document.querySelectorAll('#productRecipeRows .recipe-row')) {
        const mid = row.querySelector('.recipe-material').value;
        const qty = parseFloat(row.querySelector('.recipe-consume').value);
        if (!mid) continue;
        if (!(qty > 0)) continue;
        const idNum = parseInt(mid, 10);
        if (seen.has(idNum)) {
            showToast('同一原料不能重复添加', 'error');
            return;
        }
        seen.add(idNum);
        items.push({ material_id: idNum, consume_qty: qty });
    }
    if (items.length === 0) {
        if (!confirm('未填写有效配方行，保存后将清空该商品配方。确定吗？')) return;
    }
    try {
        const response = await fetchWithToken(`${API_BASE_URL}/api/product/${productId}/recipe`, {
            method: 'PUT',
            body: JSON.stringify({ items })
        });
        if (!response) return;
        const result = await response.json();
        if (!result.success) throw new Error(result.msg || '保存失败');
        showToast('配方已保存', 'success');
        closeProductRecipeModal();
    } catch (error) {
        console.error('保存配方失败:', error);
        showToast('保存失败: ' + (error.message || ''), 'error');
    }
}

function openPurchaseModal() {
    document.getElementById('purchaseModal').classList.remove('hidden');
    setTimeout(() => {
        document.getElementById('purchaseModalContent').classList.remove('scale-95', 'opacity-0');
        document.getElementById('purchaseModalContent').classList.add('scale-100', 'opacity-100');
    }, 10);
    loadMaterialsSelect();
}

function closePurchaseModal() {
    const content = document.getElementById('purchaseModalContent');
    content.classList.remove('scale-100', 'opacity-100');
    content.classList.add('scale-95', 'opacity-0');
    setTimeout(() => {
        document.getElementById('purchaseModal').classList.add('hidden');
    }, 300);
}

async function loadMaterialsSelect() {
    try {
        const response = await fetchWithToken(`${API_BASE_URL}/api/material/list`);
        if (!response) return;
        const result = await response.json();
        if (!result.success) throw new Error(result.msg || '加载原料失败');
        const options = (result.data || []).map(m => `<option value="${m.id}">${m.name} (${m.unit})</option>`).join('');
        document.querySelectorAll('.purchase-material').forEach(select => {
            if (select.options.length <= 1) {
                select.innerHTML = '<option value="">选择原料</option>' + options;
            }
        });
    } catch (error) {
        console.error('加载原料失败:', error);
    }
}

function addPurchaseItem() {
    const container = document.getElementById('purchaseItems');
    const newItem = document.createElement('div');
    newItem.className = 'flex gap-3 items-end purchase-item';
    newItem.innerHTML = `
                <div class="flex-1 min-w-0">
                    <select class="w-full px-4 py-2 rounded-lg border border-amber-200 focus:outline-none focus:border-amber-500 purchase-material">
                        <option value="">选择原料</option>
                    </select>
                    <input type="text" class="mt-1 w-full px-3 py-1.5 text-sm rounded-lg border border-amber-100 focus:outline-none focus:border-amber-400 purchase-material-name" placeholder="无选项时可手动输入名称（新建原料）" autocomplete="off">
                    <input type="text" class="mt-1 w-full px-3 py-1.5 text-sm rounded-lg border border-amber-100 focus:outline-none focus:border-amber-400 purchase-material-unit" placeholder="手动单位，如 g / ml / 包" autocomplete="off">
                </div>
                <div class="w-24">
                    <input type="number" class="w-full px-4 py-2 rounded-lg border border-amber-200 focus:outline-none focus:border-amber-500 purchase-qty" placeholder="0" min="1">
                </div>
                <div class="w-32">
                    <input type="number" class="w-full px-4 py-2 rounded-lg border border-amber-200 focus:outline-none focus:border-amber-500 purchase-price" placeholder="0.00" min="0" step="0.01">
                </div>
                <button onclick="removePurchaseItem(this)" class="mb-2 text-red-500 hover:text-red-700">
                    <i class="fas fa-trash"></i>
                </button>
            `;
    container.appendChild(newItem);
    loadMaterialsSelect();
}

function removePurchaseItem(btn) {
    btn.closest('.purchase-item').remove();
    calculatePurchaseTotal();
}

function calculatePurchaseTotal() {
    let total = 0;
    document.querySelectorAll('.purchase-item').forEach(item => {
        const qty = parseFloat(item.querySelector('.purchase-qty').value) || 0;
        const price = parseFloat(item.querySelector('.purchase-price').value) || 0;
        total += qty * price;
    });
    document.getElementById('purchaseTotal').textContent = `¥${total.toFixed(2)}`;
}

function setupEventListeners() {
    const purchaseItemsEl = document.getElementById('purchaseItems');
    purchaseItemsEl.addEventListener('input', (e) => {
        if (e.target.classList.contains('purchase-material-name')) {
            const row = e.target.closest('.purchase-item');
            if (row) {
                const sel = row.querySelector('.purchase-material');
                if (sel && (e.target.value || '').trim()) sel.value = '';
            }
        }
        if (e.target.classList.contains('purchase-material-unit')) {
            const row = e.target.closest('.purchase-item');
            if (row) {
                const sel = row.querySelector('.purchase-material');
                if (sel && (e.target.value || '').trim()) sel.value = '';
            }
        }
        calculatePurchaseTotal();
    });
    purchaseItemsEl.addEventListener('change', (e) => {
        if (e.target.classList.contains('purchase-material')) {
            const row = e.target.closest('.purchase-item');
            if (!row) return;
            const nameIn = row.querySelector('.purchase-material-name');
            const unitIn = row.querySelector('.purchase-material-unit');
            if (nameIn && e.target.value) nameIn.value = '';
            if (unitIn && e.target.value) unitIn.value = '';
        }
    });

    document.getElementById('memberModal').addEventListener('click', (e) => {
        if (e.target.id === 'memberModal') closeMemberModal();
    });
    document.getElementById('purchaseModal').addEventListener('click', (e) => {
        if (e.target.id === 'purchaseModal') closePurchaseModal();
    });
    document.getElementById('orderDetailModal').addEventListener('click', (e) => {
        if (e.target.id === 'orderDetailModal') closeOrderDetailModal();
    });
    document.getElementById('productModal').addEventListener('click', (e) => {
        if (e.target.id === 'productModal') closeProductModal();
    });
    document.getElementById('productRecipeModal').addEventListener('click', (e) => {
        if (e.target.id === 'productRecipeModal') closeProductRecipeModal();
    });
    document.getElementById('memberDetailModal').addEventListener('click', (e) => {
        if (e.target.id === 'memberDetailModal') closeMemberDetailModal();
    });
    document.getElementById('purchaseDetailModal').addEventListener('click', (e) => {
        if (e.target.id === 'purchaseDetailModal') closePurchaseDetailModal();
    });
    document.getElementById('stockAdjustModal').addEventListener('click', (e) => {
        if (e.target.id === 'stockAdjustModal') closeStockAdjustModal();
    });
    const searchInput = document.getElementById('memberSearch');
    if(searchInput) {
        searchInput.addEventListener('input', debounce(searchMembers, 300));
    }
}

function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

function logout() {
    if (confirm('确定要退出登录吗？')) {
        localStorage.removeItem('token');
        window.location.href = '/html/Login.html';
    }
}

// 分页切换
function changePage(delta) {
    const newPage = currentPage + delta;
    if(newPage < 1) return;
    loadOrdersData(newPage);
}

// 后端搜索版本（大数据量）
async function searchMembersBackend() {
    const keyword = document.getElementById('memberSearch').value.trim();

    if(!keyword) {
        loadMembersData(); // 重新加载全部
        return;
    }

    try {
        const response = await fetchWithToken(
            `${API_BASE_URL}/api/member/search?keyword=${encodeURIComponent(keyword)}`
        );
        if(!response) return;

        const result = await response.json();
        if(!result.success) throw new Error(result.msg);

        renderMembersTable(result.data);

    } catch(error) {
        console.error('搜索会员失败:', error);
        showToast('搜索失败', 'error');
    }
}

// 加载库存数据
async function loadInventoryData(signal = null) {
    try {
        const [materialsRes, logsRes] = await Promise.all([
            fetchWithToken(`${API_BASE_URL}/api/material/list`, { signal }),
            fetchWithToken(`${API_BASE_URL}/api/inventory_log/recent?limit=10`, { signal })
        ]);

        if(!materialsRes || !logsRes) {
            console.log('Inventory request cancelled or failed');
            return;
        }
        if (document.getElementById('inventory').classList.contains('hidden')) return;

        const materialsResult = await materialsRes.json();
        const logsResult = await logsRes.json();

        if(!materialsResult.success) throw new Error(materialsResult.msg);
        if(!logsResult.success) throw new Error(logsResult.msg);

        // ✅ 修复：使用 window.materialsData 或声明变量
        window.materialsData = materialsResult.data;  // 全局存储
        const logs = logsResult.data;

        // 渲染库存列表
        renderInventoryTable(window.materialsData);

        // 渲染库存流水
        renderInventoryLogs(logs);

        // 更新工作台预警数量
        const lowStockCount = window.materialsData.filter(m => m.is_low).length;
        document.getElementById('lowStockCount').textContent = lowStockCount;

    } catch(error) {
        if (error.name === 'AbortError') return;
        console.error('加载库存数据失败:', error);
        showToast('加载库存数据失败', 'error');
    }
}

// 渲染库存表格
function renderInventoryTable(materials) {
    const tbody = document.getElementById('inventoryTableBody');

    if(materials.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" class="text-center text-amber-400 py-8">暂无原料</td></tr>';
        return;
    }

    tbody.innerHTML = materials.map(material => {
        const isLow = material.is_low;
        return `
                <tr class="${isLow ? 'bg-red-50' : ''}">
                    <td class="py-4 px-6 text-sm text-amber-700">#${material.id}</td>
                    <td class="py-4 px-6 font-medium text-amber-900">${material.name}</td>
                    <td class="py-4 px-6 text-sm">${material.unit}</td>
                    <td class="py-4 px-6 ${isLow ? 'text-red-600 font-bold' : 'text-amber-900'}">
                        ${material.stock_quantity}
                        ${isLow ? '<i class="fas fa-exclamation-triangle text-red-500 ml-1"></i>' : ''}
                    </td>
                    <td class="py-4 px-6 text-sm text-gray-600">${material.safety_stock}</td>
                    <td class="py-4 px-6">
                        <span class="status-badge ${material.status === 1 ? 'status-success' : 'status-danger'}">
                            ${material.status === 1 ? '启用' : '停用'}
                        </span>
                    </td>
                    <td class="py-4 px-6 text-sm text-gray-500">${material.updated_at}</td>
                    <td class="py-4 px-6">
                        <button onclick="adjustStock(${material.id})" class="text-amber-600 hover:text-amber-800 text-sm font-medium">调整</button>
                    </td>
                </tr>
            `;
    }).join('');
}

// 渲染库存流水
function renderInventoryLogs(logs) {
    const container = document.getElementById('inventoryLogs');

    if(logs.length === 0) {
        container.innerHTML = '<div class="text-center text-amber-400 py-4">暂无记录</div>';
        return;
    }

    container.innerHTML = logs.map(log => `
            <div class="flex items-center justify-between p-3 bg-amber-50 rounded-lg text-sm">
                <div>
                    <p class="font-medium text-amber-900">${log.material_name}</p>
                    <p class="text-xs text-amber-600">${log.type_name} | ${log.created_at}</p>
                </div>
                <div class="text-right">
                    <p class="font-bold ${log.quantity > 0 ? 'text-green-600' : 'text-red-600'}">
                        ${log.quantity > 0 ? '+' : ''}${log.quantity}
                    </p>
                    <p class="text-xs text-gray-500">余: ${log.after_stock}</p>
                </div>
            </div>
        `).join('');
}

function showLowStock() {
    if(!window.materialsData) return;

    const lowStockMaterials = window.materialsData.filter(m => m.is_low);
    renderInventoryTable(lowStockMaterials);

    // 添加返回按钮
    const tbody = document.getElementById('inventoryTableBody');
    const returnBtn = `
            <tr>
                <td colspan="8" class="py-4 px-6 text-center">
                    <button onclick="renderInventoryTable(window.materialsData)" class="text-amber-600 hover:text-amber-800 text-sm font-medium">
                        <i class="fas fa-arrow-left mr-1"></i>返回全部
                    </button>
                </td>
            </tr>
        `;
    tbody.insertAdjacentHTML('afterbegin', returnBtn);
}

async function loadWithLock(key, asyncFn, signal) {
    // 如果已有进行中的加载，取消它
    if (loadingStates.has(key)) {
        const oldController = loadingStates.get(key);
        oldController.abort();
    }

    // 创建新的 controller
    const controller = new AbortController();
    loadingStates.set(key, controller);

    // 如果外部 signal 取消，也取消内部
    if (signal) {
        signal.addEventListener('abort', () => controller.abort());
    }

    try {
        return await asyncFn(controller.signal);
    } finally {
        loadingStates.delete(key);
    }
}

function showPageLoading(tabName) {
    // 根据你的 HTML 结构实现，例如：
    const loadingEl = document.getElementById(`${tabName}Loading`);
    if (loadingEl) loadingEl.classList.remove('hidden');
}

function hidePageLoading(tabName) {
    const loadingEl = document.getElementById(`${tabName}Loading`);
    if (loadingEl) loadingEl.classList.add('hidden');
}

// 其他功能占位
function showNotifications() { showToast('暂无新通知', 'info'); }
function filterOrders(type) {
    currentOrderFilter = ['all', 'today', 'week'].includes(type) ? type : 'all';
    document.querySelectorAll('.order-filter-btn').forEach(btn => {
        const isActive = btn.dataset.orderFilter === currentOrderFilter;
        btn.classList.toggle('bg-amber-500', isActive);
        btn.classList.toggle('text-white', isActive);
        btn.classList.toggle('bg-amber-100', !isActive);
        btn.classList.toggle('text-amber-700', !isActive);
    });
    loadOrdersData(1);
}
function exportOrders() { showToast('导出功能开发中', 'info'); }
async function cancelOrder(orderNo) {
    if (!confirm(`确认取消订单 ${orderNo} 吗？`)) return;
    try {
        const response = await fetchWithToken(`${API_BASE_URL}/api/sale_order/${orderNo}/cancel`, {
            method: 'POST'
        });
        if (!response) return;
        const result = await response.json();
        if (!result.success) throw new Error(result.msg || '取消失败');

        showToast('订单已取消', 'success');
        loadOrdersData(currentPage);
        // 页面联动：若工作台或库存页可见则刷新
        if (!document.getElementById('dashboard').classList.contains('hidden')) {
            loadDashboardData();
        }
        if (!document.getElementById('inventory').classList.contains('hidden')) {
            loadInventoryData();
        }
    } catch (error) {
        console.error('取消订单失败:', error);
        showToast('取消订单失败: ' + (error.message || ''), 'error');
    }
}
async function rechargeMember(memberId) {
    const amountText = prompt('请输入充值金额（元）');
    if (amountText === null) return;
    const amount = parseFloat(amountText);
    if (!amount || amount <= 0) {
        showToast('请输入大于0的金额', 'error');
        return;
    }
    try {
        const response = await fetchWithToken(`${API_BASE_URL}/api/member/${memberId}/recharge`, {
            method: 'POST',
            body: JSON.stringify({
                amount: amount,
                remark: '前端会员列表充值'
            })
        });
        if (!response) return;
        const result = await response.json();
        if (!result.success) throw new Error(result.msg || '充值失败');
        showToast('充值成功', 'success');
        loadMembersData();
    } catch (error) {
        console.error('会员充值失败:', error);
        showToast('充值失败: ' + (error.message || ''), 'error');
    }
}
async function viewMemberDetail(memberId) {
    try {
        const response = await fetchWithToken(`${API_BASE_URL}/api/member/${memberId}/detail`);
        if (!response) return;
        const result = await response.json();
        if (!result.success) throw new Error(result.msg || '加载会员详情失败');

        const d = result.data || {};
        const logs = (d.recent_account_logs || []).slice(0, 8);
        const orders = (d.recent_orders || []).slice(0, 8);

        const content = document.getElementById('memberDetailContent');
        content.innerHTML = `
            <div class="space-y-5">
                <div class="grid grid-cols-2 gap-4 text-sm">
                    <div class="p-3 bg-amber-50 rounded-lg"><span class="text-amber-600">会员ID：</span><span class="font-medium text-amber-900">#${d.id}</span></div>
                    <div class="p-3 bg-amber-50 rounded-lg"><span class="text-amber-600">手机号：</span><span class="font-medium text-amber-900">${d.phone || '-'}</span></div>
                    <div class="p-3 bg-amber-50 rounded-lg"><span class="text-amber-600">姓名：</span><span class="font-medium text-amber-900">${d.name || '-'}</span></div>
                    <div class="p-3 bg-amber-50 rounded-lg"><span class="text-amber-600">等级：</span><span class="font-medium text-amber-900">${getMemberLevelText(d.level)}</span></div>
                    <div class="p-3 bg-amber-50 rounded-lg"><span class="text-amber-600">余额：</span><span class="font-medium text-amber-900">¥${Number(d.balance || 0).toFixed(2)}</span></div>
                    <div class="p-3 bg-amber-50 rounded-lg"><span class="text-amber-600">积分：</span><span class="font-medium text-amber-900">${d.points || 0}</span></div>
                    <div class="p-3 bg-amber-50 rounded-lg"><span class="text-amber-600">累计消费：</span><span class="font-medium text-amber-900">¥${Number(d.total_consume || 0).toFixed(2)}</span></div>
                    <div class="p-3 bg-amber-50 rounded-lg"><span class="text-amber-600">状态：</span><span class="font-medium ${d.status === 1 ? 'text-green-600' : 'text-red-500'}">${d.status === 1 ? '正常' : '冻结'}</span></div>
                </div>

                <div>
                    <h4 class="font-bold text-amber-900 mb-2">最近账务流水</h4>
                    ${
                        logs.length === 0
                            ? '<div class="text-xs bg-gray-50 border border-amber-100 rounded-lg p-3 text-gray-500">无</div>'
                            : `
                                <div class="overflow-x-auto border border-amber-100 rounded-lg">
                                    <table class="w-full text-xs">
                                        <thead class="bg-amber-50 text-amber-700">
                                            <tr>
                                                <th class="text-left px-3 py-2">时间</th>
                                                <th class="text-left px-3 py-2">类型</th>
                                                <th class="text-right px-3 py-2">余额变动</th>
                                                <th class="text-right px-3 py-2">积分变动</th>
                                                <th class="text-left px-3 py-2">备注</th>
                                            </tr>
                                        </thead>
                                        <tbody class="bg-white text-gray-700">
                                            ${logs.map(log => `
                                                <tr class="border-t border-amber-50">
                                                    <td class="px-3 py-2 whitespace-nowrap">${log.created_at || '-'}</td>
                                                    <td class="px-3 py-2">${log.biz_type || '-'}</td>
                                                    <td class="px-3 py-2 text-right ${Number(log.delta_balance || 0) >= 0 ? 'text-green-600' : 'text-red-500'}">${Number(log.delta_balance || 0).toFixed(2)}</td>
                                                    <td class="px-3 py-2 text-right ${Number(log.delta_points || 0) >= 0 ? 'text-green-600' : 'text-red-500'}">${log.delta_points || 0}</td>
                                                    <td class="px-3 py-2">${log.remark || '-'}</td>
                                                </tr>
                                            `).join('')}
                                        </tbody>
                                    </table>
                                </div>
                            `
                    }
                </div>

                <div>
                    <h4 class="font-bold text-amber-900 mb-2">最近订单</h4>
                    ${
                        orders.length === 0
                            ? '<div class="text-xs bg-gray-50 border border-amber-100 rounded-lg p-3 text-gray-500">无</div>'
                            : `
                                <div class="overflow-x-auto border border-amber-100 rounded-lg">
                                    <table class="w-full text-xs">
                                        <thead class="bg-amber-50 text-amber-700">
                                            <tr>
                                                <th class="text-left px-3 py-2">时间</th>
                                                <th class="text-left px-3 py-2">订单号</th>
                                                <th class="text-right px-3 py-2">金额</th>
                                                <th class="text-left px-3 py-2">状态</th>
                                            </tr>
                                        </thead>
                                        <tbody class="bg-white text-gray-700">
                                            ${orders.map(o => `
                                                <tr class="border-t border-amber-50">
                                                    <td class="px-3 py-2 whitespace-nowrap">${o.created_at || '-'}</td>
                                                    <td class="px-3 py-2 font-mono">${o.order_no || '-'}</td>
                                                    <td class="px-3 py-2 text-right">¥${Number(o.pay_amount || 0).toFixed(2)}</td>
                                                    <td class="px-3 py-2">${getStatusText(o.status)}</td>
                                                </tr>
                                            `).join('')}
                                        </tbody>
                                    </table>
                                </div>
                            `
                    }
                </div>
            </div>
        `;

        document.getElementById('memberDetailModal').classList.remove('hidden');
        setTimeout(() => {
            document.getElementById('memberDetailModalContent').classList.remove('scale-95', 'opacity-0');
            document.getElementById('memberDetailModalContent').classList.add('scale-100', 'opacity-100');
        }, 10);
    } catch (error) {
        console.error('加载会员详情失败:', error);
        showToast('加载会员详情失败: ' + (error.message || ''), 'error');
    }
}

function closeMemberDetailModal() {
    const content = document.getElementById('memberDetailModalContent');
    content.classList.remove('scale-100', 'opacity-100');
    content.classList.add('scale-95', 'opacity-0');
    setTimeout(() => {
        document.getElementById('memberDetailModal').classList.add('hidden');
    }, 300);
}
function openStockAdjustModal() {
    if (!window.materialsData || window.materialsData.length === 0) {
        showToast('请先加载库存数据', 'warning');
        return;
    }
    const select = document.getElementById('stockAdjustMaterial');
    select.innerHTML = window.materialsData.map(m =>
        `<option value="${m.id}">${m.name}（当前:${m.stock_quantity}${m.unit || ''}）</option>`
    ).join('');
    if (currentAdjustMaterialId) {
        select.value = String(currentAdjustMaterialId);
    }
    document.getElementById('stockAdjustDelta').value = '';
    document.getElementById('stockAdjustRemark').value = '盘点调整';

    document.getElementById('stockAdjustModal').classList.remove('hidden');
    setTimeout(() => {
        document.getElementById('stockAdjustModalContent').classList.remove('scale-95', 'opacity-0');
        document.getElementById('stockAdjustModalContent').classList.add('scale-100', 'opacity-100');
    }, 10);
}

function closeStockAdjustModal() {
    const content = document.getElementById('stockAdjustModalContent');
    content.classList.remove('scale-100', 'opacity-100');
    content.classList.add('scale-95', 'opacity-0');
    setTimeout(() => {
        document.getElementById('stockAdjustModal').classList.add('hidden');
    }, 300);
}

async function submitStockAdjust() {
    const materialId = parseInt(document.getElementById('stockAdjustMaterial').value);
    const deltaQty = parseFloat(document.getElementById('stockAdjustDelta').value);
    const remark = (document.getElementById('stockAdjustRemark').value || '').trim() || '盘点调整';
    if (!materialId) {
        showToast('请选择原料', 'error');
        return;
    }
    if (isNaN(deltaQty) || deltaQty === 0) {
        showToast('请输入非0数字', 'error');
        return;
    }
    try {
        const response = await fetchWithToken(`${API_BASE_URL}/api/material/${materialId}/adjust`, {
            method: 'POST',
            body: JSON.stringify({
                delta_qty: deltaQty,
                remark
            })
        });
        if (!response) return;
        const result = await response.json();
        if (!result.success) throw new Error(result.msg || '调整失败');
        showToast('库存调整成功', 'success');
        closeStockAdjustModal();
        loadInventoryData();
        if (!document.getElementById('dashboard').classList.contains('hidden')) {
            loadDashboardData();
        }
    } catch (error) {
        console.error('库存调整失败:', error);
        showToast('库存调整失败: ' + (error.message || ''), 'error');
    }
}

function adjustStock(materialId) {
    currentAdjustMaterialId = materialId;
    openStockAdjustModal();
}
async function confirmInbound(orderId) {
    if (!confirm('确认将该采购单入库吗？')) return;
    try {
        const response = await fetchWithToken(`${API_BASE_URL}/api/purchase_order/${orderId}/confirm_inbound`, {
            method: 'POST'
        });
        if (!response) return;
        const result = await response.json();
        if (!result.success) throw new Error(result.msg || '入库失败');

        showToast('确认入库成功', 'success');
        loadPurchaseData();
        // 入库后刷新库存模块，便于联调观察库存变化
        if (!document.getElementById('inventory').classList.contains('hidden')) {
            loadInventoryData();
        }
    } catch (error) {
        console.error('确认入库失败:', error);
        showToast('确认入库失败: ' + (error.message || ''), 'error');
    }
}

async function viewPurchaseDetail(orderId) {
    try {
        const response = await fetchWithToken(`${API_BASE_URL}/api/purchase_order/${orderId}/detail`);
        if (!response) return;
        const result = await response.json();
        if (!result.success) throw new Error(result.msg || '加载详情失败');
        const d = result.data || {};
        const items = d.items || [];
        const content = document.getElementById('purchaseDetailContent');
        content.innerHTML = `
            <div class="space-y-5">
                <div class="grid grid-cols-2 gap-4 text-sm">
                    <div class="p-3 bg-amber-50 rounded-lg"><span class="text-amber-600">采购单号：</span><span class="font-mono font-medium text-amber-900">${d.order_no || '-'}</span></div>
                    <div class="p-3 bg-amber-50 rounded-lg"><span class="text-amber-600">供应商：</span><span class="font-medium text-amber-900">${d.supplier || '-'}</span></div>
                    <div class="p-3 bg-amber-50 rounded-lg"><span class="text-amber-600">状态：</span><span class="font-medium text-amber-900">${getPurchaseStatusText(d.status)}</span></div>
                    <div class="p-3 bg-amber-50 rounded-lg"><span class="text-amber-600">总金额：</span><span class="font-medium text-amber-900">¥${Number(d.total_amount || 0).toFixed(2)}</span></div>
                    <div class="p-3 bg-amber-50 rounded-lg"><span class="text-amber-600">操作人：</span><span class="font-medium text-amber-900">${d.operator || '-'}</span></div>
                    <div class="p-3 bg-amber-50 rounded-lg"><span class="text-amber-600">创建时间：</span><span class="font-medium text-amber-900">${d.created_at || '-'}</span></div>
                </div>

                <div>
                    <h4 class="font-bold text-amber-900 mb-2">采购明细</h4>
                    ${
                        items.length === 0
                            ? '<div class="text-xs bg-gray-50 border border-amber-100 rounded-lg p-3 text-gray-500">无</div>'
                            : `
                                <div class="overflow-x-auto border border-amber-100 rounded-lg">
                                    <table class="w-full text-xs">
                                        <thead class="bg-amber-50 text-amber-700">
                                            <tr>
                                                <th class="text-left px-3 py-2">原料</th>
                                                <th class="text-right px-3 py-2">数量</th>
                                                <th class="text-right px-3 py-2">单价</th>
                                                <th class="text-right px-3 py-2">小计</th>
                                            </tr>
                                        </thead>
                                        <tbody class="bg-white text-gray-700">
                                            ${items.map(i => `
                                                <tr class="border-t border-amber-50">
                                                    <td class="px-3 py-2">${i.material_name || '-'}</td>
                                                    <td class="px-3 py-2 text-right">${i.quantity}</td>
                                                    <td class="px-3 py-2 text-right">${Number(i.unit_price || 0).toFixed(2)}</td>
                                                    <td class="px-3 py-2 text-right">${Number(i.subtotal || 0).toFixed(2)}</td>
                                                </tr>
                                            `).join('')}
                                        </tbody>
                                    </table>
                                </div>
                            `
                    }
                </div>
            </div>
        `;

        document.getElementById('purchaseDetailModal').classList.remove('hidden');
        setTimeout(() => {
            document.getElementById('purchaseDetailModalContent').classList.remove('scale-95', 'opacity-0');
            document.getElementById('purchaseDetailModalContent').classList.add('scale-100', 'opacity-100');
        }, 10);
    } catch (error) {
        console.error('加载采购详情失败:', error);
        showToast('加载采购详情失败: ' + (error.message || ''), 'error');
    }
}

function closePurchaseDetailModal() {
    const content = document.getElementById('purchaseDetailModalContent');
    content.classList.remove('scale-100', 'opacity-100');
    content.classList.add('scale-95', 'opacity-0');
    setTimeout(() => {
        document.getElementById('purchaseDetailModal').classList.add('hidden');
    }, 300);
}

