// Customer-facing storefront. API settings belong on docs.html.
// Find an existing control using its ID from the HTML file.
function getPageElement(elementId) {

    return document.getElementById(elementId);
}


const shoppingBag = new Map();

let productCollection = [];

let selectedCategory = 'All';

// These drawings are local SVG artwork, not product data from another website.


function createProductIllustration(name) {
    const productNameText = name.toLowerCase();

    let productDrawing;

    if (productNameText.includes('notebook')) {
        productDrawing = '<rect x="73" y="44" width="112" height="161" rx="5" fill="#c7ba95"/><rect x="68" y="39" width="112" height="160" rx="4" fill="#e4d6ad"/><path d="M80 39v160" stroke="#667651" stroke-width="13"/><rect x="99" y="75" width="55" height="35" fill="#eee6cb"/><path d="M110 86h32m-32 10h20" stroke="#a6a081" stroke-width="2"/><path d="M196 54l-19 141" stroke="#a57d48" stroke-width="7"/><path d="M177 195l-3 14 8-12" fill="#51513e"/>';
    } else if (productNameText.includes('lamp')) {
        productDrawing = '<ellipse cx="128" cy="210" rx="51" ry="10" fill="#d5c3a9"/><path d="M125 197v-89l25-44" fill="none" stroke="#7b7053" stroke-width="8"/><ellipse cx="127" cy="199" rx="43" ry="8" fill="#a8956c"/><path d="M126 52l53 26-14 25-66-33z" fill="#b98e59"/><ellipse cx="132" cy="89" rx="37" ry="10" transform="rotate(27 132 89)" fill="#ddbd83"/>';
    } else if (productNameText.includes('headphone')) {
        productDrawing = '<path d="M73 146v-30a56 56 0 0 1 112 0v30" fill="none" stroke="#647578" stroke-width="18"/><path d="M80 107a49 49 0 0 1 98 0" fill="none" stroke="#a9b6b5" stroke-width="7"/><rect x="57" y="125" width="39" height="68" rx="17" fill="#5b6e71"/><rect x="161" y="125" width="39" height="68" rx="17" fill="#5b6e71"/><rect x="79" y="132" width="14" height="53" rx="6" fill="#b2bcba"/><rect x="165" y="132" width="14" height="53" rx="6" fill="#b2bcba"/>';
    } else if (productNameText.includes('mug')) {
        productDrawing = '<ellipse cx="125" cy="206" rx="53" ry="8" fill="#d1d2bf"/><path d="M161 90h23c35 0 35 57 0 57h-19" fill="none" stroke="#9b9e81" stroke-width="13"/><path d="M77 76h96l-10 121c-16 16-60 16-76 0z" fill="#c9ccad"/><ellipse cx="125" cy="76" rx="48" ry="10" fill="#788467"/><path d="M86 91l7 95" stroke="#e3e5ce" stroke-width="9"/><path d="M109 44c-12-15 13-17 1-31m32 31c-12-15 13-17 1-31" stroke="#b7bdac" stroke-width="3" fill="none"/>';
    } else if (productNameText.includes('keyboard') || productNameText.includes('mouse')) {
        productDrawing = '<rect x="36" y="90" width="184" height="87" rx="9" fill="#c6c8b8"/><rect x="42" y="95" width="172" height="72" rx="6" fill="#e2e3d5"/>';

        for (let rowNumber = 0; rowNumber < 3; rowNumber++) {
            for (let columnNumber = 0; columnNumber < 10; columnNumber++) {
                productDrawing += '<rect x="' + (49 + columnNumber * 16) + '" y="' + (104 + rowNumber * 16) + '" width="12" height="11" rx="2" fill="#b6bda6"/>';
            }
        }
    } else {
        productDrawing = '<path d="M103 64V48c0-28 51-28 51 0v16" fill="none" stroke="#536248" stroke-width="11"/><path d="M94 58h70c26 0 35 26 35 58v90c-28 15-110 15-138 0v-90c0-32 10-58 33-58z" fill="#6d7d55"/><path d="M75 85c27-15 81-15 108 0" fill="none" stroke="#9caa80" stroke-width="4"/><rect x="88" y="139" width="84" height="65" rx="11" fill="#85956a"/><path d="M97 151h66" stroke="#5b6e46" stroke-width="3"/><rect x="114" y="96" width="31" height="24" rx="2" fill="#c9bb94"/><path d="M121 105h17m-17 6h11" stroke="#8c886c" stroke-width="2"/>';
    }

    return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" aria-hidden="true">' + productDrawing + '</svg>';
}


// Use the product name to group this small demo catalog into categories.
function getProductCategory(product) {
    const productName = product.name.toLowerCase();

    if (productName.includes('notebook') || productName.includes('keyboard') || productName.includes('mouse')) {
        return 'Desk';
    }

    if (productName.includes('lamp')) {
        return 'Home';
    }

    if (productName.includes('headphone')) {
        return 'Audio';
    }

    if (productName.includes('backpack') || productName.includes('mug')) {
        return 'On the go';
    }

    return 'Essentials';
}


// Rebuild the category buttons when a new product collection is loaded.
function renderCategories() {
    getPageElement('categories').replaceChildren();

    for (const categoryName of [
        'All', ...new Set(productCollection.map(getProductCategory))
    ]) {
        const button = document.createElement('button');

        button.textContent = categoryName;

        button.className = categoryName === selectedCategory ? 'active' : '';

        button.setAttribute('aria-pressed', String(categoryName === selectedCategory));

        button.onclick = () => {
            selectedCategory = categoryName;
            renderCategories();
            renderProducts();
        };

        getPageElement('categories').append(button);
    }
}


// Show the products matching the search text and selected category.
function renderProducts() {
    getPageElement('products').replaceChildren();

    const queryParameters = getPageElement('search').value.trim().toLowerCase();

    const visibleProducts = productCollection.filter(product => (selectedCategory === 'All' || getProductCategory(product) === selectedCategory)
        && product.name.toLowerCase().includes(queryParameters));

    getPageElement('productCount').textContent = '(' + visibleProducts.length + ')';

    for (const product of visibleProducts) {
        const productCard = document.createElement('article');

        productCard.className = 'product';

        const productArtwork = document.createElement('div');

        productArtwork.className = 'productArt';

        productArtwork.innerHTML = createProductIllustration(product.name);
        // Only the fixed SVG templates above produce HTML.
        const productTag = document.createElement('span');

        productTag.className = 'productTag';
        productTag.textContent = 'The everyday edit';

        const addToBagButton = document.createElement('button');

        addToBagButton.className = 'addButton';
        addToBagButton.textContent = '+';

        addToBagButton.setAttribute('aria-label', 'Add ' + product.name + ' to bag');

        addToBagButton.onclick = () => {
            const bagItem = shoppingBag.get(product.id);

            if (bagItem) {
                bagItem.quantity++;
            } else {
                shoppingBag.set(product.id, {
                    ...product,
                    quantity: 1
                });
            }

            renderBag();

            addToBagButton.textContent = '✓';
        };

        productArtwork.append(productTag, addToBagButton);

        const categoryElement = document.createElement('div');
        categoryElement.className = 'category';
        categoryElement.textContent = getProductCategory(product);

        const productNameElement = document.createElement('h3');
        productNameElement.textContent = product.name;

        const productPriceElement = document.createElement('div');
        productPriceElement.className = 'price';
        productPriceElement.textContent = '$' + product.price.toFixed(2);

        productCard.append(productArtwork, categoryElement, productNameElement, productPriceElement);

        getPageElement('products').append(productCard);
    }

    getPageElement('storeMessage').textContent = visibleProducts.length ? '' : 'No pieces match your search. Try a different word or category.';
}


// Recalculate quantities and totals from the bag stored in this page's memory.
function renderBag() {
    getPageElement('bagItems').replaceChildren();

    let totalPrice = 0;

    let totalQuantity = 0;

    for (const bagItem of shoppingBag.values()) {
        totalPrice += bagItem.price * bagItem.quantity;

        totalQuantity += bagItem.quantity;

        const bagRow = document.createElement('div');
        bagRow.className = 'bagItem';

        const titleElement = document.createElement('div');

        const productNameElement = document.createElement('strong');
        productNameElement.textContent = bagItem.name;

        const productPriceElement = document.createElement('small');
        productPriceElement.textContent = '$' + bagItem.price.toFixed(2) + ' each';

        titleElement.append(productNameElement, productPriceElement);

        const quantityControls = document.createElement('div');
        quantityControls.className = 'quantity';

        const decreaseQuantityButton = document.createElement('button');
        decreaseQuantityButton.textContent = '−';
        decreaseQuantityButton.setAttribute('aria-label', 'Remove one ' + bagItem.name);

        const quantityDisplay = document.createElement('span');
        quantityDisplay.textContent = bagItem.quantity;

        const increaseQuantityButton = document.createElement('button');
        increaseQuantityButton.textContent = '+';
        increaseQuantityButton.setAttribute('aria-label', 'Add one more ' + bagItem.name);

        decreaseQuantityButton.onclick = () => {
            bagItem.quantity--;
            if (bagItem.quantity === 0) {
                shoppingBag.delete(bagItem.id);
            }
            renderBag();
        };

        increaseQuantityButton.onclick = () => {
            bagItem.quantity++;
            renderBag();
        };

        quantityControls.append(decreaseQuantityButton, quantityDisplay, increaseQuantityButton);
        bagRow.append(titleElement, quantityControls);
        getPageElement('bagItems').append(bagRow);
    }

    if (!shoppingBag.size) {
        getPageElement('bagItems').textContent = 'Your bag is waiting for a little everyday upgrade.';
    }

    getPageElement('bagCount').textContent = totalQuantity;

    getPageElement('bagTotal').textContent = '$' + totalPrice.toFixed(2);
}


// Ask MockForge for the catalog. A failed response shows the retry message.
async function loadCollection() {
    getPageElement('retry').hidden = true;

    getPageElement('storeMessage').textContent = 'Getting the collection ready…';

    try {
        const queryParameters = new URLSearchParams(location.search);

        let workspaceId = Number(queryParameters.get('workspaceId'));

        if (!workspaceId) {
            const response = await fetch('/api/workspaces', {
                cache: 'no-store'
            });

            if (!response.ok) {
                throw new Error('Workspace lookup failed');
            }

            const workspaceRecords = await response.json();

            workspaceId = workspaceRecords.find(workspace => workspace.name === 'Shopping Demo')?.workspaceId;
        }

        const route = queryParameters.get('route') || '/products';

        if (!Number.isSafeInteger(workspaceId) || workspaceId < 1 || !/^\/(?!\/)/.test(route) || /[?#%]/.test(route)) {
            throw new Error('Invalid collection configuration');
        }

        const response = await fetch('/mock/workspaces/' + workspaceId + route, {
            cache: 'no-store',

            signal: AbortSignal.timeout(10000)
        });

        if (!response.ok) {
            throw new Error('Collection request returned ' + response.status);
        }

        const responseData = await response.json();

        if (!Array.isArray(responseData) || responseData.some(product => !product || product.id == null || typeof product.name !== 'string' || !Number.isFinite(product.price) || product.price < 0)) {
            throw new Error('Invalid product list');
        }

        productCollection = responseData;

        selectedCategory = 'All';

        renderCategories();
        renderProducts();
    } catch (error) {
        productCollection = [];
        renderCategories();
        getPageElement('products').replaceChildren();
        getPageElement('productCount').textContent = '(0)';

        getPageElement('storeMessage').textContent = 'We couldn’t load the collection right now. Please try again shortly.';

        getPageElement('retry').hidden = false;

        console.error('Collection could not load:', error.message);
    }
}

getPageElement('heroObject').innerHTML = createProductIllustration('backpack');

getPageElement('openBag').onclick = () => {
    renderBag();
    getPageElement('bagDialog').showModal();
};

getPageElement('closeBag').onclick = () => getPageElement('bagDialog').close();

getPageElement('continueShopping').onclick = () => getPageElement('bagDialog').close();

getPageElement('search').oninput = renderProducts;

getPageElement('retry').onclick = loadCollection;

renderBag();

loadCollection();
