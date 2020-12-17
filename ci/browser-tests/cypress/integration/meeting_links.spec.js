describe('Meeting Links Test', function () {
    before(() => {
        cy.dummyLogin("Danny")
        cy.selectLanguage("ENG")
        cy.projectByName("Alatskivi")
    })

    it('sort searched cadastral units ', function () {
        cy.get("h1").contains("Alatskivi")

        cy.get("button.project-menu").click()
        cy.get("li.project-menu-item-project-meetings").click()

        let sel = 'a:contains("Test meeting #3 #1")'
        cy.get(sel).click()
        cy.get('h3:contains("Test topic")').click()

        const select = "#links-type-select"
        const option = `[data-item*=':cadastral-unit']`

        cy.get(option).then(($opt) => {
            cy.get(select).select($opt.attr("value"))
        })

        cy.wait(1000)

        const search = `input[placeholder='Link...']`
        cy.get(search).type("Tartu")

        cy.wait(1000)
    })
})