from playwright.sync_api import sync_playwright
def run(playwright):
    browser = playwright.chromium.launch(headless=True)
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
    page.fill("input[type=\"password\"]", "testing123")
    # Click text=Login as Benjamin Boss (Maanteeamet)arrow_forward
    # with page.expect_navigation(url="http://localhost:4000/#/admin/indexes"):
    with page.expect_navigation():
        page.click("text=Login as Benjamin Boss (Maanteeamet)arrow_forward")
    # Click button:has-text("addLisan uue indeksi")
    # with page.expect_navigation(url="http://localhost:4000/#/admin/indexes?add-index-form=1"):
    with page.expect_navigation():
        page.click("button:has-text(\"addLisan uue indeksi\")")
    # Click text=Lisan uue indeksiIndeksi nimetus *Indeksi tüüp *- ValinTarbijahinna indexBituume >> input
    page.click("text=Lisan uue indeksiIndeksi nimetus *Indeksi tüüp *- ValinTarbijahinna indexBituume >> input")
    # Fill text=Lisan uue indeksiIndeksi nimetus *Indeksi tüüp *- ValinTarbijahinna indexBituume >> input
    page.fill("text=Lisan uue indeksiIndeksi nimetus *Indeksi tüüp *- ValinTarbijahinna indexBituume >> input", "best index")
    # Select 2
    page.select_option("select", "2")
    # Click text=Kehtiv alates *calendar_today >> input
    page.click("text=Kehtiv alates *calendar_today >> input")
    # Fill text=Kehtiv alates *calendar_today >> input
    page.fill("text=Kehtiv alates *calendar_today >> input", "01.01.2000")
    # Click button:has-text("Salvestan")
    page.click("button:has-text(\"Salvestan\")")
    # Click text=My Most Excellent Index
    page.click("text=My Most Excellent Index")
    # assert page.url == "http://localhost:4000/#/admin/index/87960930223108"
    # Click button:has-text("Muudan indeksit")
    page.click("button:has-text(\"Muudan indeksit\")")
    # Click button:has-text("Tühistan")
    page.click("button:has-text(\"Tühistan\")")
    # Click button:has-text("addLisan uue indeksi")
    # with page.expect_navigation(url="http://localhost:4000/#/admin/indexes?add-index-form=1"):
    with page.expect_navigation():
        page.click("button:has-text(\"addLisan uue indeksi\")")
    # Click text=Lisan uue indeksiIndeksi nimetus *Indeksi tüüp *- ValinTarbijahinna indexBituume >> input
    page.click("text=Lisan uue indeksiIndeksi nimetus *Indeksi tüüp *- ValinTarbijahinna indexBituume >> input")
    # Fill text=Lisan uue indeksiIndeksi nimetus *Indeksi tüüp *- ValinTarbijahinna indexBituume >> input
    page.fill("text=Lisan uue indeksiIndeksi nimetus *Indeksi tüüp *- ValinTarbijahinna indexBituume >> input", "Frank")
    # Select 1
    page.select_option("select", "1")
    # Click button:has-text("calendar_today")
    page.click("button:has-text(\"calendar_today\")")
    # Click button:has-text("Täna")
    page.click("button:has-text(\"Täna\")")
    # Click text=TühistanSalvestan >> div
    page.click("text=TühistanSalvestan >> div")
    # Click text=Frank
    page.click("text=Frank")
    # assert page.url == "http://localhost:4000/#/admin/index/101155069756422"
    # Click button:has-text("Muudan")
    page.click("button:has-text(\"Muudan\")")
    # Click text=Indeksi nimetusKustutanTühistanSalvestan >> input
    page.click("text=Indeksi nimetusKustutanTühistanSalvestan >> input")
    # Fill text=Indeksi nimetusKustutanTühistanSalvestan >> input
    page.fill("text=Indeksi nimetusKustutanTühistanSalvestan >> input", "Frank II")
    # Click button:has-text("Salvestan")
    page.click("button:has-text(\"Salvestan\")")
    # ---------------------
    context.close()
    browser.close()
with sync_playwright() as playwright:
    run(playwright)
