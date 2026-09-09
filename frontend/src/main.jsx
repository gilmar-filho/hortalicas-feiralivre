import {
  ArrowRight,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Clock3,
  LogOut,
  MapPin,
  PackageCheck,
  Plus,
  Search,
  ShoppingBasket,
  Sprout,
  X,
} from "lucide-react";
import { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import "./styles.css";

const API = "http://localhost:8080/api";
const money = (value) =>
  Number(value).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });

function App() {
  const [path, setPath] = useState(window.location.pathname);
  const [mode, setMode] = useState(
    { "/comprar": "buy", "/vender": "sell" }[window.location.pathname] || null,
  );
  const [products, setProducts] = useState([]);
  const [orders, setOrders] = useState([]);
  const [invoices, setInvoices] = useState([]);
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState(null);
  const [toast, setToast] = useState("");
  const [loading, setLoading] = useState(false);
  const [showRegistration, setShowRegistration] = useState(false);
  const [productForm, setProductForm] = useState(null);
  const [currentUser, setCurrentUser] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem("feira-user")) || null;
    } catch {
      return null;
    }
  });
  const navigate = (target) => {
    window.history.pushState({}, "", target);
    setPath(target);
    setMode({ "/comprar": "buy", "/vender": "sell" }[target] || null);
  };
  useEffect(() => {
    const onPopState = () => {
      setPath(window.location.pathname);
      setMode(
        { "/comprar": "buy", "/vender": "sell" }[window.location.pathname] ||
          null,
      );
    };
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, []);

  const loadProducts = async (search = "") => {
    const ownerFilter = mode === "sell" && currentUser ? `&usuarioId=${currentUser.id}` : "";
    const response = await fetch(
      `${API}/produtos?busca=${encodeURIComponent(search)}${ownerFilter}`,
    );
    setProducts(await response.json());
  };
  const loadOrders = async () => {
    if (!currentUser) return;
    setOrders(await (await fetch(`${API}/pedidos?compradorId=${currentUser.id}`)).json());
  };
  const loadInvoices = async () =>
    currentUser && setInvoices(
      await (await fetch(`${API}/faturamento?visao=vendedor&usuarioId=${currentUser.id}`)).json(),
    );
  useEffect(() => {
    if (currentUser) loadProducts();
    loadOrders();
    loadInvoices();
  }, [currentUser, mode]);
  useEffect(() => {
    const timer = setInterval(loadOrders, 3000);
    return () => clearInterval(timer);
  }, [currentUser]);
  useEffect(() => {
    const timer = setTimeout(() => loadProducts(query), 250);
    return () => clearTimeout(timer);
  }, [query]);
  const notify = (message) => {
    setToast(message);
    setTimeout(() => setToast(""), 3500);
  };

  async function finishOrder(data) {
    setLoading(true);
    try {
      const response = await fetch(`${API}/pedidos`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...data, compradorId: currentUser.id }),
      });
      const body = await response.json();
      if (!response.ok)
        throw new Error(
          body.detail || body.message || "Não foi possível reservar o estoque.",
        );
      setSelected(null);
      await Promise.all([loadProducts(query), loadOrders(), loadInvoices()]);
      notify("Pedido criado com status PENDENTE.");
    } catch (error) {
      notify(error.message);
    } finally {
      setLoading(false);
    }
  }

  if (!mode && path === "/login")
    return (
        <LoginPage
        onLogin={(user) => {
          localStorage.setItem("feira-user", JSON.stringify(user));
          setCurrentUser(user);
          navigate("/comprar");
        }}
        onRegister={() => navigate("/cadastro")}
      />
    );
  if (!mode && path === "/cadastro")
    return (
      <RegistrationPage
        onBack={() => navigate("/login")}
        onSaved={() => navigate("/login")}
      />
    );
  if (!mode)
    return (
      <Landing
        onChoose={() => navigate("/login")}
        onRegister={() => navigate("/cadastro")}
      />
    );
  return (
    <div className="app-shell">
      <header className="topbar">
        <button className="brand" onClick={() => navigate("/login")}>
          <span className="brand-mark">f</span> feira livre
        </button>
        <nav>
          <button
            className={mode === "buy" ? "active" : ""}
            onClick={() => navigate("/comprar")}
          >
            <ShoppingBasket size={17} /> Comprar
          </button>
          <button
            className={mode === "sell" ? "active" : ""}
            onClick={() => navigate("/vender")}
          >
            <Sprout size={17} /> Vender
          </button>
        </nav>
        <div className="profile">
          <span className="avatar">{currentUser?.nome?.slice(0, 2).toUpperCase()}</span>
          <span>{currentUser?.nome}</span>
          <button
            className="icon-button"
            onClick={() => {
              localStorage.removeItem("feira-user");
              setCurrentUser(null);
              navigate("/login");
            }}
            title="Sair"
          >
            <LogOut size={17} />
          </button>
        </div>
      </header>
      {mode === "buy" ? (
        <BuyView
          products={products}
          query={query}
          setQuery={setQuery}
          onSelect={setSelected}
          orders={orders}
        />
      ) : (
        <>
          <SellView
            products={products}
            invoices={invoices}
            notify={notify}
            onForm={setProductForm}
            onInvoices={loadInvoices}
            onStatusChanged={async () => {
              await Promise.all([
                loadProducts(query),
                loadOrders(),
                loadInvoices(),
              ]);
            }}
            userId={currentUser.id}
          />
          <InvoiceReading invoices={invoices} />
        </>
      )}
      {selected && (
        <Checkout
          product={selected}
          onClose={() => setSelected(null)}
          onSubmit={finishOrder}
          loading={loading}
        />
      )}
      {productForm && (
          <ProductForm
          product={productForm === "new" ? null : productForm}
            userId={currentUser.id}
          onClose={() => setProductForm(null)}
          onSaved={async (message) => {
            setProductForm(null);
            await loadProducts(query);
            notify(message);
          }}
        />
      )}
      {toast && (
        <div className="toast">
          <PackageCheck size={18} />
          {toast}
        </div>
      )}
      {showRegistration && (
        <Registration
          onClose={() => setShowRegistration(false)}
          onSaved={notify}
        />
      )}
    </div>
  );
}

function Landing({ onChoose, onRegister }) {
  return (
    <main className="landing">
      <div className="grain" />
      <header className="landing-nav">
        <span className="brand light">
          <span className="brand-mark">f</span> feira livre
        </span>
        <div className="landing-links">
          <button className="ghost" onClick={onRegister}>
            Cadastro
          </button>
          <button className="ghost" onClick={onChoose}>
            Login
          </button>
        </div>
      </header>
      <section className="hero">
        <div className="hero-copy">
          <p className="eyebrow">FEIRA DE HORTALIÇAS</p>
          <h1>
            Quem colhe
            <br />
            vende. Quem
            <br />
            <em>cozinha</em> compra.
          </h1>
          <p className="intro">
            Um único cadastro para os dois lados: anuncie sua produção, compre
            de outros produtores e combine a retirada no local, no dia e no
            horário que funcionam para vocês.
          </p>
          <div className="hero-actions">
            <button className="primary" onClick={onChoose}>
              Login <ArrowRight size={17} />
            </button>
            <button className="ghost" onClick={onRegister}>
              Criar cadastro
            </button>
          </div>
        </div>
        <div className="hero-image">
          <img
            src="https://images.unsplash.com/photo-1542838132-92c53300491e?auto=format&fit=crop&w=1200&q=85"
            alt="Hortaliças frescas"
          />
          <span className="illustrative-label">Foto ilustrativa</span>
          <div className="image-caption">
            <span>01</span>
            <span>Da horta para sua mesa</span>
          </div>
        </div>
      </section>
    </main>
  );
}

function LoginPage({ onLogin, onRegister }) {
  const [data, setData] = useState({ email: "", senha: "" });
  const [error, setError] = useState("");
  const submit = async (event) => {
    event.preventDefault();
    const response = await fetch(`${API}/usuario/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    });
    if (response.ok) onLogin(await response.json());
    else setError("E-mail ou senha inválidos.");
  };
  return (
    <main className="auth-page">
      <form className="checkout auth-form" onSubmit={submit}>
        <span className="brand">
          <span className="brand-mark">f</span> feira livre
        </span>
        <p className="eyebrow dark">ENTRAR</p>
        <h2>Bem-vindo à feira.</h2>
        <label>
          E-mail
          <input
            type="email"
            value={data.email}
            onChange={(event) =>
              setData({ ...data, email: event.target.value })
            }
            required
          />
        </label>
        <label>
          Senha
          <input
            type="password"
            value={data.senha}
            onChange={(event) =>
              setData({ ...data, senha: event.target.value })
            }
            required
          />
        </label>
        {error && <p className="form-error">{error}</p>}
        <button className="primary wide">
          Entrar <ArrowRight size={17} />
        </button>
        <button type="button" className="text-button" onClick={onRegister}>
          Ainda não tenho cadastro
        </button>
      </form>
    </main>
  );
}

function RegistrationPage({ onBack, onSaved }) {
  const [data, setData] = useState({ nome: "", email: "", senha: "" });
  const submit = async (event) => {
    event.preventDefault();
    const response = await fetch(`${API}/usuario`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    });
    if (response.ok) onSaved();
    else alert("Não foi possível criar o cadastro.");
  };
  return (
    <main className="auth-page">
      <form className="checkout auth-form" onSubmit={submit}>
        <button type="button" className="close" onClick={onBack}>
          <X size={20} />
        </button>
        <span className="brand">
          <span className="brand-mark">f</span> feira livre
        </span>
        <p className="eyebrow dark">CADASTRO</p>
        <h2>Uma conta para comprar e vender.</h2>
        <label>
          Nome
          <input
            value={data.nome}
            onChange={(event) => setData({ ...data, nome: event.target.value })}
            required
          />
        </label>
        <label>
          E-mail
          <input
            type="email"
            value={data.email}
            onChange={(event) =>
              setData({ ...data, email: event.target.value })
            }
            required
          />
        </label>
        <label>
          Senha
          <input
            type="password"
            minLength="6"
            value={data.senha}
            onChange={(event) =>
              setData({ ...data, senha: event.target.value })
            }
            required
          />
        </label>
        <button className="primary wide">
          Criar cadastro <ArrowRight size={17} />
        </button>
      </form>
    </main>
  );
}

function BuyView({ products, query, setQuery, onSelect, orders }) {
  const [category, setCategory] = useState("Tudo");
  const [productsPage, setProductsPage] = useState(1);
  const [ordersPage, setOrdersPage] = useState(1);
  const pageSize = 5;
  const categories = ["Tudo", "Folhas", "Raízes", "Hortaliças", "Ervas", "Frutas"];
  const normalizeCategory = (value) => value.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase();
  const categoryFor = (product) => {
    if (product.categoria) {
      const storedCategory = normalizeCategory(product.categoria);
      const selectedCategory = categories.find((item) => normalizeCategory(item) === storedCategory);
      if (selectedCategory) return selectedCategory;
    }
    const name = product.nome.toLowerCase();
    if (/alface|couve|espinafre|rúcula|rucula/.test(name)) return "Folhas";
    if (/cenoura|beterraba|batata|mandioca|rabanete/.test(name)) return "Raízes";
    if (/tomate|abobrinha|pepino|piment/.test(name)) return "Hortaliças";
    if (/manjericão|manjericao|salsinha|coentro|hortelã|hortela/.test(name)) return "Ervas";
    if (/maçã|maca|banana|morango|laranja/.test(name)) return "Frutas";
    return "Hortaliças";
  };
  const visibleProducts = products.filter((product) => category === "Tudo" || categoryFor(product) === category);
  const pagedProducts = visibleProducts.slice(
    (productsPage - 1) * pageSize,
    productsPage * pageSize,
  );
  const visibleOrders = orders.slice((ordersPage - 1) * pageSize, ordersPage * pageSize);
  return (
    <main className="content catalog-page">
      <div className="page-heading">
        <div>
          <p className="eyebrow dark">MERCADO LOCAL</p>
          <h1>Catálogo da feira</h1>
          <p className="catalog-count">{visibleProducts.length} produtos disponíveis</p>
        </div>
      </div>
      <div className="toolbar">
        <div className="search">
          <Search size={18} />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Buscar por produto..."
          />
        </div>
        <div className="category-filters">
          {categories.map((item) => <button key={item} className={category === item ? "selected" : ""} onClick={() => setCategory(item)}>{item}</button>)}
        </div>
      </div>
      <section className="product-grid">
        {pagedProducts.map((product) => (
          <ProductCard
            key={product.id}
            product={product}
            onClick={() => onSelect(product)}
          />
        ))}
      </section>
      <Pagination
        page={productsPage}
        pageSize={pageSize}
        total={visibleProducts.length}
        onChange={setProductsPage}
      />
      <section className="orders-section">
        <div className="section-title">
          <div>
            <p className="eyebrow dark">ACOMPANHAMENTO</p>
            <h2>Meus pedidos</h2>
          </div>
          <span>{orders.length} pedidos</span>
        </div>
        {orders.length === 0 ? (
          <p className="empty">Seus pedidos aparecerão aqui.</p>
        ) : (
          <div className="orders-list">
            {visibleOrders.map((order) => (
              <div className="order-row" key={order.id}>
                <div className="order-number">
                  #{String(order.id).padStart(4, "0")}
                </div>
                <div>
                  <strong>Retirada em {order.data_retirada}</strong>
                  <small>
                    {order.produtos || "Compra registrada"}
                  </small>
                  <small>
                    {order.local_nome} · {order.hora_retirada} · Comprador: {order.comprador_nome}
                  </small>
                  <small>
                    Fatura #{order.fatura_id || "-"} ·{" "}
                    {order.fatura_status || "não gerada"}
                  </small>
                </div>
                <span className={`status ${order.status.toLowerCase()}`}>
                  {order.status}
                </span>
                <strong>
                  {money(order.fatura_valor || order.valor_total)}
                </strong>
              </div>
            ))}
          </div>
        )}
        <Pagination
          page={ordersPage}
          pageSize={pageSize}
          total={orders.length}
          onChange={setOrdersPage}
        />
      </section>
    </main>
  );
}

function Pagination({ page, pageSize, total, onChange }) {
  const pageCount = Math.ceil(total / pageSize);
  if (pageCount <= 1) return null;
  return (
    <div className="pagination">
      <button
        className="pagination-button"
        aria-label="Página anterior"
        title="Página anterior"
        disabled={page === 1}
        onClick={() => onChange(page - 1)}
      >
        <ChevronLeft size={16} />
      </button>
      <span className="pagination-label">
        {page} de {pageCount}
      </span>
      <button
        className="pagination-button"
        aria-label="Próxima página"
        title="Próxima página"
        disabled={page === pageCount}
        onClick={() => onChange(page + 1)}
      >
        <ChevronRight size={16} />
      </button>
    </div>
  );
}
function ProductCard({ product, onClick }) {
  const image =
    product.foto ||
    "https://images.unsplash.com/photo-1597362925123-77861d3fbac7?auto=format&fit=crop&w=700&q=80";
  return (
    <article className="product-card" onClick={onClick}>
      <div className="product-photo">
        <img src={image} alt={product.nome} onError={(event) => { event.currentTarget.src = "https://images.unsplash.com/photo-1597362925123-77861d3fbac7?auto=format&fit=crop&w=700&q=80"; }} />
        <span className="fresh">fresco</span>
      </div>
      <div className="product-info">
        <div>
          <h3>{product.nome}</h3>
          <p>{product.descricao}</p>
        </div>
        <strong className="price">
          {money(product.preco)}
          <small>/ un.</small>
        </strong>
      </div>
      <div className="product-meta">
        <span>
          <span className="dot green" /> {product.estoque} disponíveis
        </span>
        <span>
          válido até{" "}
          {product.validade
            ? new Date(`${product.validade}T00:00:00`).toLocaleDateString(
                "pt-BR",
              )
            : "indisponível"}
        </span>
      </div>
      <button className="card-action">
        Ver detalhes <ArrowRight size={16} />
      </button>
    </article>
  );
}

function Checkout({ product, onClose, onSubmit, loading }) {
  const [quantity, setQuantity] = useState(1);
  const [date, setDate] = useState("");
  const [time, setTime] = useState("09:00");
  return (
    <div className="modal-backdrop">
      <div className="checkout">
        <button className="close" onClick={onClose}>
          <X size={20} />
        </button>
        <p className="eyebrow dark">FINALIZAR PEDIDO</p>
        <h2>{product.nome}</h2>
        <p className="checkout-description">{product.descricao}</p>
        <div className="checkout-total">
          <span>
            {quantity} unidade{quantity > 1 ? "s" : ""} × {money(product.preco)}
          </span>
          <strong>{money(product.preco * quantity)}</strong>
        </div>
        <label>
          Quantidade
          <input
            type="number"
            min="1"
            max={product.estoque}
            value={quantity}
            onChange={(e) =>
              setQuantity(
                Math.max(1, Math.min(product.estoque, Number(e.target.value))),
              )
            }
          />
        </label>
        <div className="two-fields">
          <label>
            <CalendarDays size={16} /> Data de retirada
            <input
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              required
            />
          </label>
          <label>
            <Clock3 size={16} /> Horário
            <select value={time} onChange={(e) => setTime(e.target.value)}>
              <option>09:00</option>
              <option>10:00</option>
              <option>11:00</option>
            </select>
          </label>
        </div>
        <div className="pickup">
          <MapPin size={18} />
          <div>
            <strong>Feira Central</strong>
            <span>Praça da Matriz, 100 · sábado, 08h às 12h</span>
          </div>
        </div>
        <button
          className="primary wide"
          disabled={loading || !date}
          onClick={() =>
            onSubmit({
              produtoId: product.id,
              quantidade: quantity,
              dataRetirada: date,
              horaRetirada: time,
              localRetiradaId: 1,
            })
          }
        >
          {loading ? "Processando..." : "Confirmar e pagar"}{" "}
          <ArrowRight size={17} />
        </button>
      </div>
    </div>
  );
}

function SellView({
  products,
  invoices,
  notify,
  onForm,
  onInvoices,
  onStatusChanged,
  userId,
}) {
  const [invoiceForm, setInvoiceForm] = useState(null);
  const [productsPage, setProductsPage] = useState(1);
  const [invoicesPage, setInvoicesPage] = useState(1);
  const pageSize = 5;
  const visibleProducts = products.slice(
    (productsPage - 1) * pageSize,
    productsPage * pageSize,
  );
  const visibleInvoices = invoices.slice((invoicesPage - 1) * pageSize, invoicesPage * pageSize);
  return (
    <main className="content seller">
      <div className="page-heading">
        <div>
          <p className="eyebrow dark">ÁREA DO PRODUTOR</p>
          <h1>
            Sua horta,
            <br />
            <em>seu negócio.</em>
          </h1>
        </div>
        <button className="primary" onClick={() => onForm("new")}>
          <Plus size={17} /> Novo produto
        </button>
      </div>
      <div className="seller-stats">
        <div>
          <span>Produtos ativos</span>
          <strong>{products.length}</strong>
        </div>
        <div>
          <span>Estoque total</span>
          <strong>
            {products.reduce((sum, p) => sum + Number(p.estoque || 0), 0)}
          </strong>
        </div>
        <div>
          <span>Faturas</span>
          <strong>{invoices.length}</strong>
        </div>
      </div>
      <div className="section-title">
        <div>
          <p className="eyebrow dark">CATÁLOGO</p>
          <h2>Meus produtos</h2>
        </div>
        <span>Atualizado agora</span>
      </div>
      <section className="seller-list">
        {visibleProducts.map((product) => (
          <div className="seller-product" key={product.id}>
            <img src={product.foto} alt="" />
            <div>
              <strong>{product.nome}</strong>
              <span>{product.descricao}</span>
            </div>
            <strong>{money(product.preco)}</strong>
            <span className="stock">
              {product.estoque} em estoque · vence{" "}
              {product.data_validade
                ? new Date(
                    `${product.data_validade}T00:00:00`,
                  ).toLocaleDateString("pt-BR")
                : "-"}
            </span>
            <button className="outline" onClick={() => onForm(product)}>
              Editar
            </button>
          </div>
        ))}
      </section>
      <Pagination
        page={productsPage}
        pageSize={pageSize}
        total={products.length}
        onChange={setProductsPage}
      />
      <SellerOrders
        notify={notify}
        vendedorId={userId}
        onStatusChanged={onStatusChanged}
      />
      <div className="section-title invoice-heading">
        <div>
          <p className="eyebrow dark">FATURAMENTO</p>
          <h2>Faturas dos pedidos</h2>
        </div>
        <span>{invoices.length} registradas</span>
      </div>
      <section className="invoice-list">
        {invoices.length === 0 ? (
          <p className="empty">As faturas dos pedidos aparecerão aqui.</p>
        ) : (
          visibleInvoices.map((invoice) => (
            <div className="invoice-row" key={invoice.id}>
              <div>
                <strong>
                  Fatura #{invoice.id} · Pedido #{invoice.pedido_id}
                </strong>
                <small>
                  Status do pedido: {invoice.pedido_status}
                </small>
              </div>
              <strong>{money(invoice.valor)}</strong>
              <span className={`status ${invoice.status.toLowerCase()}`}>
                {invoice.status}
              </span>
              <button
                className="outline"
                onClick={() => setInvoiceForm(invoice)}
              >
                Editar
              </button>
            </div>
          ))
        )}
      </section>
      {invoices[0]?.proxima_retirada && (
        <div className="seller-note">
          <Sprout size={22} />
          <span>
            <strong>Próxima retirada</strong> em {invoices[0].proxima_retirada} · Fatura #{invoices[0].proxima_retirada_fatura_id}
          </span>
          <MapPin size={17} />
        </div>
      )}
      {invoiceForm && (
        <InvoiceForm
          invoice={invoiceForm}
          onClose={() => setInvoiceForm(null)}
          onSaved={async (message) => {
            setInvoiceForm(null);
            await onInvoices();
            notify(message);
          }}
        />
      )}
    </main>
  );
}

function SellerOrders({ notify, vendedorId, onStatusChanged }) {
  const [orders, setOrders] = useState([]);
  const [ordersPage, setOrdersPage] = useState(1);
  const pageSize = 5;
  const load = async () =>
    setOrders(await (await fetch(`${API}/pedidos/recebidos?vendedorId=${vendedorId}`)).json());
  useEffect(() => {
    load();
  }, [vendedorId]);
  const uniqueOrders = Array.from(
    new Map(orders.map((order) => [order.id, order])).values(),
  );
  const visibleOrders = uniqueOrders.slice(
    (ordersPage - 1) * pageSize,
    ordersPage * pageSize,
  );
  const update = async (id, status) => {
    await fetch(`${API}/pedidos/${id}/status`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status, vendedorId }),
    });
    await Promise.all([load(), onStatusChanged()]);
    notify("Status do pedido atualizado.");
  };
  return (
    <section className="orders-section seller-orders">
      <div className="section-title">
        <div>
          <p className="eyebrow dark">PEDIDOS RECEBIDOS</p>
          <h2>Pedidos dos compradores</h2>
        </div>
        <span>{uniqueOrders.length} pedidos</span>
      </div>
      {uniqueOrders.length === 0 ? (
        <p className="empty">Nenhum pedido recebido.</p>
      ) : (
        <div className="orders-list">
          {visibleOrders.map((order) => (
            <div className="order-row" key={order.id}>
              <div className="order-number">
                #{String(order.id).padStart(4, "0")}
              </div>
              <div>
                <strong>{order.comprador_nome}</strong>
                <small>
                  Retirada em {order.data_retirada} · {money(order.valor_total)}
                </small>
              </div>
              <select
                value={order.status}
                onChange={(event) => update(order.id, event.target.value)}
              >
                <option>PENDENTE</option>
                <option>CONFIRMADO</option>
                <option>SEPARADO</option>
                <option>PRONTO</option>
                <option>ENTREGUE</option>
                <option>CANCELADO</option>
              </select>
            </div>
          ))}
        </div>
      )}
      <Pagination
        page={ordersPage}
        pageSize={pageSize}
        total={uniqueOrders.length}
        onChange={setOrdersPage}
      />
    </section>
  );
}

function ProductForm({ product, onClose, onSaved, userId }) {
  const [data, setData] = useState({
    nome: product?.nome || "",
    categoria: product?.categoria || "Hortaliças",
    descricao: product?.descricao || "",
    foto: product?.foto || "",
    preco: product?.preco || "",
    ativo: product ? Boolean(product.ativo) : true,
    dataCadastro:
      product?.data_cadastro || new Date().toISOString().slice(0, 10),
    loteId: product?.lote_id || "",
    dataValidade: product?.data_validade || "",
    quantidadeEstoque: product?.quantidade_disponivel ?? "",
    localId: product?.local_retirada_id || "",
    localNome: product?.local_nome || "Feira Central",
    localEndereco: product?.local_endereco || "Praça da Matriz, 100 - Centro",
  });
  const change = (event) =>
    setData({
      ...data,
      [event.target.name]:
        event.target.type === "checkbox"
          ? event.target.checked
          : event.target.value,
    });
  const submit = async (event) => {
    event.preventDefault();
    const body = {
      ...data,
      usuarioId: Number(userId),
      preco: Number(data.preco),
      ativo: data.ativo ? 1 : 0,
      quantidadeEstoque: Number(data.quantidadeEstoque),
    };
    const response = await fetch(
      product ? `${API}/produtos/${product.id}` : `${API}/produtos`,
      {
        method: product ? "PUT" : "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      },
    );
    if (!response.ok) return;
    await onSaved(
      product
        ? "Produto, validade e estoque atualizados."
        : "Produto e lote criados com sucesso.",
    );
  };
  return (
    <div className="modal-backdrop">
      <form className="checkout" onSubmit={submit}>
        <button type="button" className="close" onClick={onClose}>
          <X size={20} />
        </button>
        <p className="eyebrow dark">
          {product ? "EDITAR PRODUTO" : "NOVO PRODUTO"}
        </p>
        <h2>{product ? product.nome : "Cadastrar produto"}</h2>
        <label>
          Categoria do produto
          <select name="categoria" value={data.categoria} onChange={change} required>
            <option>Folhas</option>
            <option>Raízes</option>
            <option>Hortaliças</option>
            <option>Ervas</option>
            <option>Frutas</option>
          </select>
        </label>
        {[
          ["nome", "Nome"],
          ["descricao", "Descrição"],
          ["foto", "URL da foto"],
          ["preco", "Preço"],
          ["dataCadastro", "Data de cadastro"],
          ["dataValidade", "Data de vencimento"],
          ["quantidadeEstoque", "Quantidade em estoque"],
          ["localNome", "Local de retirada"],
          ["localEndereco", "Endereço de retirada"],
        ].map(([name, label]) => (
          <label key={name}>
            {label}
            <input
              name={name}
              value={data[name]}
              onChange={change}
              required={name !== "foto"}
              type={
                name === "preco" || name === "quantidadeEstoque"
                  ? "number"
                  : name === "dataCadastro" || name === "dataValidade"
                    ? "date"
                    : "text"
              }
              min={name === "quantidadeEstoque" ? "0" : undefined}
              step={name === "preco" ? "0.01" : undefined}
            />
          </label>
        ))}
        <label className="payment-toggle">
          <input
            name="ativo"
            type="checkbox"
            checked={data.ativo}
            onChange={change}
          />{" "}
          Produto ativo
        </label>
        <button className="primary wide">
          Salvar produto <ArrowRight size={17} />
        </button>
      </form>
    </div>
  );
}

function InvoiceForm({ invoice, onClose, onSaved }) {
  const [data, setData] = useState({
    valor: invoice.valor,
    status: invoice.status,
  });
  const submit = async (event) => {
    event.preventDefault();
    const response = await fetch(`${API}/faturamento/${invoice.id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ valor: Number(data.valor), status: data.status }),
    });
    if (!response.ok) return;
    await onSaved("Fatura atualizada com sucesso.");
  };
  return (
    <div className="modal-backdrop">
      <form className="checkout" onSubmit={submit}>
        <button type="button" className="close" onClick={onClose}>
          <X size={20} />
        </button>
        <p className="eyebrow dark">EDITAR FATURA #{invoice.id}</p>
        <h2>Pedido #{invoice.pedido_id}</h2>
        <label>
          Valor
          <input
            type="number"
            min="0"
            step="0.01"
            value={data.valor}
            onChange={(event) =>
              setData({ ...data, valor: event.target.value })
            }
            required
          />
        </label>
        <label>
          Status
          <select
            value={data.status}
            onChange={(event) =>
              setData({ ...data, status: event.target.value })
            }
          >
            <option>PENDENTE</option>
            <option>APROVADO</option>
            <option>RECUSADO</option>
            <option>ESTORNADO</option>
          </select>
        </label>
        <button className="primary wide">
          Salvar fatura <ArrowRight size={17} />
        </button>
      </form>
    </div>
  );
}

function Registration({ onClose, onSaved }) {
  const [data, setData] = useState({ nome: "", email: "", senha: "" });
  const submit = async (event) => {
    event.preventDefault();
    const response = await fetch(`${API}/usuario`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    });
    if (response.ok) {
      onClose();
      onSaved("Cadastro criado com sucesso.");
    } else onSaved("Não foi possível criar o cadastro.");
  };
  return (
    <div className="modal-backdrop">
      <form className="checkout" onSubmit={submit}>
        <button type="button" className="close" onClick={onClose}>
          <X size={20} />
        </button>
        <p className="eyebrow dark">SEU CADASTRO</p>
        <h2>Uma conta para comprar e vender.</h2>
        <p className="checkout-description">
          O mesmo usuário pode anunciar produtos e fazer pedidos.
        </p>
        <label>
          Nome
          <input
            value={data.nome}
            onChange={(event) => setData({ ...data, nome: event.target.value })}
            required
          />
        </label>
        <label>
          E-mail
          <input
            type="email"
            value={data.email}
            onChange={(event) =>
              setData({ ...data, email: event.target.value })
            }
            required
          />
        </label>
        <label>
          Senha
          <input
            type="password"
            minLength="6"
            value={data.senha}
            onChange={(event) =>
              setData({ ...data, senha: event.target.value })
            }
            required
          />
        </label>
        <button className="primary wide">
          Criar cadastro <ArrowRight size={17} />
        </button>
      </form>
    </div>
  );
}

createRoot(document.getElementById("root")).render(<App />);

function InvoiceReading({ invoices }) {
  const [invoicesPage, setInvoicesPage] = useState(1);
  const pageSize = 5;
  const visibleInvoices = invoices.slice((invoicesPage - 1) * pageSize, invoicesPage * pageSize);
  return (
    <section className="content invoice-reading">
      <div className="section-title">
        <div>
          <p className="eyebrow dark">VISÃO FINANCEIRA</p>
          <h2>Faturas das suas vendas</h2>
        </div>
        <span>{invoices.length} registradas</span>
      </div>
      <div className="invoice-details-list">
        {invoices.length === 0 ? (
          <p className="empty">As faturas das suas vendas aparecerão aqui.</p>
        ) : (
          visibleInvoices.map((invoice) => (
            <details className="invoice-detail" key={invoice.id}>
              <summary>
                <span>
                  <strong>Fatura #{invoice.id}</strong>
                  <small>
                    Pedido #{invoice.pedido_id} · {invoice.pedido_status}
                  </small>
                </span>
                <strong>{money(invoice.valor)}</strong>
                <span className={`status ${invoice.status.toLowerCase()}`}>
                  {invoice.status}
                </span>
                <ArrowRight size={17} />
              </summary>
              <div className="invoice-detail-body">
                <div>
                  <b>Cliente</b>
                  <p>
                    {invoice.comprador_nome || "Cliente não informado"}
                    {invoice.comprador_email && <><br />{invoice.comprador_email}</>}
                  </p>
                </div>
                <div>
                  <b>Produtos</b>
                  <p>{invoice.produtos || "Dados do produto indisponíveis."}</p>
                </div>
                <div>
                  <b>Lotes e validade</b>
                  <p>{invoice.lotes || "Lote não informado."}</p>
                </div>
                <div>
                  <b>Retirada</b>
                  <p>
                    {invoice.data_retirada} às {invoice.hora_retirada}
                    <br />
                    {invoice.local_nome}
                    <br />
                    {invoice.local_endereco}
                  </p>
                </div>
                <div>
                  <b>Status</b>
                  <p>
                    {invoice.status}
                    <br />
                    Registrada em {invoice.data_pedido}
                  </p>
                </div>
              </div>
            </details>
          ))
        )}
      </div>
      <Pagination
        page={invoicesPage}
        pageSize={pageSize}
        total={invoices.length}
        onChange={setInvoicesPage}
      />
    </section>
  );
}
