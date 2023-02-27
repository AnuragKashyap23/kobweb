package com.varabyte.kobweb.navigation

import androidx.compose.runtime.*
import com.varabyte.kobweb.core.PageContext
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.url.URL

/** How to affect the current history when navigating to a new location */
enum class UpdateHistoryMode {
    /**
     * Push the new URL onto the stack, meaning if the user presses back, they return to the current URL.
     *
     * This is the most common and expected behavior when navigating within this site.
     */
    PUSH,

    /**
     * Overwrite the current URL, meaning if the user presses back, they'll go back to the URL before that one.
     *
     * This is most often useful if you aren't really navigating but are instead just changing query parameters on some
     * current page that represent transient state changes.
     */
    REPLACE
}

/**
 * Scope provided backing the handler for [Router.addRouteInterceptor].
 *
 * Through this scope, various parts of a route are exposed and can be set.
 */
class RouteInterceptorScope(pathQueryAndFragment: String) {
    private val route = Route(pathQueryAndFragment)

    /**
     * The path part of a route.
     *
     * For example, "/a/b/c" in the route "/a/b/c?key=value#fragment"
     *
     * This path should always be absolute; if you set it without a leading slash, one will
     * be added for you.
     */
    var path = route.path
        set(value) {
            field = if (value.startsWith('/')) value else "/$value"
        }

    /**
     * A map of query parameters.
     *
     * For example, { "key" = "value" } in the route "/a/b/c?key=value"
     */
    var queryParams = route.queryParams.toMutableMap()

    /**
     * The fragment part of a route, if present, or null otherwise.
     *
     * For example, "fragment" in the route "/a/b/c#fragment"
     */
    var fragment = route.fragment

    /**
     * The full route, built up of all the other parts of this scope.
     *
     * This value is read-only. To affect it, set the various parts.
     */
    val pathQueryAndFragment get() = Route(path, queryParams, fragment).toString()
}


/**
 * The class responsible for navigating to different pages in a user's app.
*/
class Router {
    private val activePageData = mutableStateOf<PageData?>(null)
    private val routeTree = RouteTree()
    private val interceptors = mutableListOf<RouteInterceptorScope.() -> Unit>()

    init {
        window.onpopstate = {
            updateActiveRoute(document.location!!.pathname)
        }
    }

    /**
     * See docs for [tryRoutingTo].
     *
     * Returns true if we were able to navigate to a route that's internal to this site; false otherwise, e.g. if the
     * path targets some external website.
     */
    private fun updateActiveRoute(pathQueryAndFragment: String): Boolean {
        // Special case - sometimes the value passed in here is simply a fragment, which means the browser
        // should scroll to an element on the same page
        if (pathQueryAndFragment.startsWith("#")) {
            activePageData.value?.pageContext?.fragment = pathQueryAndFragment.removePrefix("#")
            return activePageData.value != null
        }

        val route = Route.tryCreate(pathQueryAndFragment)
        return if (route != null) {
            activePageData.value = routeTree.createPageData(this, route)
            true
        }
        else {
            false
        }
    }

    @Suppress("unused") // Called by generated code
    @Composable
    fun renderActivePage() {
        val data = activePageData.value
            ?: error("Call 'navigateTo' at least once before calling 'renderActivePage'")

        PageContext.active.value = data.pageContext
        data.pageMethod.invoke()
    }

    /**
     * Convert the incoming navigation request into a full route (but without the origin).
     *
     * This has special handling for incoming routes that are just a fragment, e.g. "#id", or relative routes, e.g.
     * "profile" instead of "/profile"
     */
    private fun String.normalize(): String {
        if (!Route.isLocal(this)) return this

        val hrefResolved = URL(this, window.location.href)
        return interceptors.fold(Route.fromUrl(hrefResolved).toString()) { acc, intercept ->
            val interceptor = RouteInterceptorScope(acc)
            interceptor.intercept()
            interceptor.pathQueryAndFragment
        }
    }

    /**
     * Register a route, mapping it to some target composable method that will get called when that path is requested by
     * the browser.
     *
     * Routes should be internal, rooted paths, so:
     *
     * * Good: `/path`
     * * Good: `/path/with/subparts`
     * * Bad: `path`
     * * Bad: `http://othersite.com/path`
     *
     * Paths can also be dynamic routes, i.e. with parts that will consume values typed into the URL and exposed as
     * variables to the page. To accomplish this, use curly braces for that part of the path.
     *
     * For example: `/users/{user}/posts/{post}`
     *
     * In that case, if the user visited `/users/123456/posts/321`, then that composable method will be visited, with
     * `user = 123456` and `post = 321` passed down in the `PageContext`.
     *
     * @param autoPrefix If true AND if a route prefix is configured for this site, auto-affix it to the front. You
     *   usually want this to be true, unless you are intentionally linking outside this site's root folder while still
     *   staying in the same domain.
     */
    @Suppress("unused") // Called by generated code
    fun register(route: String, autoPrefix: Boolean = true, pageMethod: PageMethod) {
        require(Route.isLocal(route) && route.startsWith('/')) { "Registration only allowed for internal, rooted routes, e.g. /example/path. Got: $route" }
        require(routeTree.register(RoutePrefix.prependIf(autoPrefix, route), pageMethod)) { "Registration failure. Path is already registered: $route" }
    }

    fun setErrorHandler(errorHandler: ErrorPageMethod) {
        routeTree.errorHandler = errorHandler
    }

    /**
     * If set, get a chance to modify the page's route before Kobweb navigates to it.
     *
     * This could be a cheap way to redirect to a new URL if an old one was removed due to
     * a refactor.
     *
     * A simple way to use this might look like:
     *
     * ```
     * @InitKobweb
     * fun initKobweb(ctx: InitKobwebContext) {
     *   ctx.router.addRouteInterceptor {
     *     if (path == "admin") {
     *       // The old admin has grown and is being split up into multiple pages.
     *       // Send people to the dashboard by default.
     *       path = "admin/dashboard"
     *     }
     *   }
     * }
     * ```
     *
     * In the above case, if a user navigates to `https://yoursite.com/admin` the URL will automatically change to
     * `https://yoursite.com/admin/dashboard`.
     *
     * See [RouteInterceptorScope] for more options.
     */
    fun addRouteInterceptor(interceptor: RouteInterceptorScope.() -> Unit) {
        interceptors.add(interceptor)
    }

    @Deprecated("\"routeTo\" has been renamed to \"tryRoutingTo\".",
        ReplaceWith("tryRoutingTo(pathQueryAndFragment, updateHistoryMode, openLinkStrategy)")
    )
    fun routeTo(
        pathQueryAndFragment: String,
        updateHistoryMode: UpdateHistoryMode = UpdateHistoryMode.PUSH,
        openLinkStrategy: OpenLinkStrategy = OpenLinkStrategy.IN_PLACE): Boolean {
        return tryRoutingTo(pathQueryAndFragment, updateHistoryMode, openLinkStrategy)
    }

    /**
     * Attempt to navigate **internally** within this site, or return false if that's not possible (i.e. because the
     * path is external).
     *
     * You will generally call this method like so:
     *
     * ```
     * onClick { evt ->
     *   if (ctx.router.tryRoutingTo(...)) {
     *     evt.preventDefault()
     *   }
     * ```
     *
     * That way, either the router handles the navigation or the browser does.
     *
     * See also: [navigateTo], which, if called, handles the external navigation for you.
     *
     * @param pathQueryAndFragment The path to a page, including (optional) search params and hash,
     *   e.g. "/example/path?arg=1234#fragment". See also the
     *   [standards](https://www.rfc-editor.org/rfc/rfc3986#section-3.3) documentation.
     * @param updateHistoryMode How this new path should affect the history. See [UpdateHistoryMode] docs for more
     *   details. Note that this value will be ignored if [pathQueryAndFragment] refers to an external link.
     */
    fun tryRoutingTo(
        pathQueryAndFragment: String,
        updateHistoryMode: UpdateHistoryMode = UpdateHistoryMode.PUSH,
        openLinkStrategy: OpenLinkStrategy = OpenLinkStrategy.IN_PLACE): Boolean {
        @Suppress("NAME_SHADOWING") // Intentionally transformed
        val pathQueryAndFragment = pathQueryAndFragment.normalize()

        if (openLinkStrategy != OpenLinkStrategy.IN_PLACE) {
            window.open(pathQueryAndFragment, openLinkStrategy)
            return true
        }

        return if (updateActiveRoute(pathQueryAndFragment)) {
            // Update URL to match page we navigated to
            "${window.location.origin}$pathQueryAndFragment".let { url ->
                if (window.location.href != url) {
                    // It's possible only the search params or hash changed, in which case we don't want to reset the
                    // current page scroll
                    val newPathname = window.location.pathname != Route.fromUrl(URL(url)).path
                    when (updateHistoryMode) {
                        UpdateHistoryMode.PUSH -> window.history.pushState(window.history.state, "", url)
                        UpdateHistoryMode.REPLACE -> window.history.replaceState(window.history.state, "", url)
                    }

                    if (newPathname) {
                        document.documentElement?.scrollTop = 0.0
                    }
                }

                // Even if the URL hasn't changed, still scroll to the target element if you can. Sometimes a user might
                // scroll the page and then re-enter the same URL to go back.
                if (url.contains('#')) {
                    document.getElementById(url.substringAfter('#'))?.scrollIntoView(js("{behavior: \"smooth\"}"))
                }
            }

            true
        } else {
            false
        }
    }

    /**
     * Like [tryRoutingTo] but handle the external navigation as well.
     *
     * You will generally call this method like so:
     *
     * ```
     * onClick { evt ->
     *   evt.preventDefault()
     *   ctx.router.navigateTo(...)
     * ```
     *
     * @param updateHistoryMode This parameter is only used for internal site routing. See [tryRoutingTo] for more
     *   information.
     */
    fun navigateTo(
        pathQueryAndFragment: String,
        updateHistoryMode: UpdateHistoryMode = UpdateHistoryMode.PUSH,
        openInternalLinksStrategy: OpenLinkStrategy = OpenLinkStrategy.IN_PLACE,
        openExternalLinksStrategy: OpenLinkStrategy = OpenLinkStrategy.IN_NEW_TAB_FOREGROUND,
    ) {
        if (!tryRoutingTo(pathQueryAndFragment, updateHistoryMode, openInternalLinksStrategy)) {
            window.open(pathQueryAndFragment, openExternalLinksStrategy)
        }
    }
}