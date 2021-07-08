from playwright.sync_api import sync_playwright
def run(playwright):
    browser = playwright.chromium.launch(headless=False)
    context = browser.new_context()
    # Open new page
    page = context.new_page()
    # Go to http://localhost:4000/#/admin/indexes
    page.goto("http://localhost:4000/#/admin/indexes")
    # Go to http://localhost:4000/#/login
    page.goto("http://localhost:4000/#/login")
    # Click input[type="password"]
    page.click("input[type=\"password\"]")
    # Fill input[type="password"]
    page.fill("input[type=\"password\"]", "beetrootred")
    # Click text=Login as Benjamin Boss (Maanteeamet)arrow_forward
    # with page.expect_navigation(url="http://localhost:4000/#/admin/indexes"):
    with page.expect_navigation():
        page.click("text=Login as Benjamin Boss (Maanteeamet)arrow_forward")
    # Click button:has-text("person")
    page.click("button:has-text(\"person\")")
    # Click #EN
    page.click("#EN")
    # Click button:has-text("addAdd new index")
    # with page.expect_navigation(url="http://localhost:4000/#/admin/indexes?add-index-form=1"):
    with page.expect_navigation():
        page.click("button:has-text(\"addAdd new index\")")
    # Click text=Add new indexIndex name *Index type *- Select valueConsumer Price IndexBitumen I >> input
    page.click("text=Add new indexIndex name *Index type *- Select valueConsumer Price IndexBitumen I >> input")
    # Fill text=Add new indexIndex name *Index type *- Select valueConsumer Price IndexBitumen I >> input
    page.fill("text=Add new indexIndex name *Index type *- Select valueConsumer Price IndexBitumen I >> input", "my lovely index")
    # Select 2
    page.select_option("select", "2")
    # Click text=Valid from *calendar_today >> input
    page.click("text=Valid from *calendar_today >> input")
    # Fill text=Valid from *calendar_today >> input
    page.fill("text=Valid from *calendar_today >> input", "12.12.2012")
    # Click button:has-text("Save")
    page.click("button:has-text(\"Save\")")
    # Click button:has-text("addAdd new index")
    page.click("button:has-text(\"addAdd new index\")")
    # Click text=Add new indexIndex name *Index type *- Select valueConsumer Price IndexBitumen I >> input
    page.click("text=Add new indexIndex name *Index type *- Select valueConsumer Price IndexBitumen I >> input")
    # Fill text=Add new indexIndex name *Index type *- Select valueConsumer Price IndexBitumen I >> input
    page.fill("text=Add new indexIndex name *Index type *- Select valueConsumer Price IndexBitumen I >> input", "my fantastic index")
    # Select 1
    page.select_option("select", "1")
    # Click button:has-text("calendar_today")
    page.click("button:has-text(\"calendar_today\")")
    # Click div[role="presentation"] button:has-text("Today")
    page.click("div[role=\"presentation\"] button:has-text(\"Today\")")
    # Click button:has-text("Save")
    page.click("button:has-text(\"Save\")")
    # Click text=my lovely index
    page.click("text=my lovely index")
    # assert page.url == "http://localhost:4000/#/admin/index/87960930223112"
    # Click button:has-text("Edit")
    page.click("button:has-text(\"Edit\")")
    # Click text=Index nameDeleteCancelSave >> input
    page.click("text=Index nameDeleteCancelSave >> input")
    # Click text=Index nameDeleteCancelSave >> input
    page.click("text=Index nameDeleteCancelSave >> input")
    # Press Home
    page.press("text=Index nameDeleteCancelSave >> input", "Home")
    # Fill text=Index nameDeleteCancelSave >> input
    page.fill("text=Index nameDeleteCancelSave >> input", "your lovely index")
    # Click button:has-text("Save")
    page.click("button:has-text(\"Save\")")
    # Click button:has-text("Edit index values")
    page.click("button:has-text(\"Edit index values\")")
    # Click button:has-text("Save")
    page.click("button:has-text(\"Save\")")
    # Click text=my fantastic index
    page.click("text=my fantastic index")
    # assert page.url == "http://localhost:4000/#/admin/index/74766790689801"
    # Click button:has-text("Edit index values")
    page.click("button:has-text(\"Edit index values\")")
    # Click button:has-text("Save")
    page.click("button:has-text(\"Save\")")
    # Click text=your lovely index
    page.click("text=your lovely index")
    # assert page.url == "http://localhost:4000/#/admin/index/87960930223112"
    # Click button:has-text("Edit")
    page.click("button:has-text(\"Edit\")")
    # Click button:has-text("Delete")
    page.click("button:has-text(\"Delete\")")
    # Click #confirmation-confirm
    # with page.expect_navigation(url="http://localhost:4000/#/admin/indexes"):
    with page.expect_navigation():
        page.click("#confirmation-confirm")
    # Click text=my fantastic index
    page.click("text=my fantastic index")
    # assert page.url == "http://localhost:4000/#/admin/index/74766790689801"
    # Click button:has-text("Edit")
    page.click("button:has-text(\"Edit\")")
    # Click button:has-text("Delete")
    page.click("button:has-text(\"Delete\")")
    # Click #confirmation-confirm
    # with page.expect_navigation(url="http://localhost:4000/#/admin/indexes"):
    with page.expect_navigation():
        page.click("#confirmation-confirm")
    # ---------------------
    context.close()
    browser.close()
with sync_playwright() as playwright:
    run(playwright)
