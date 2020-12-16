describe('Meeting Links Test', function () {
    before(() => {
        cy.dummyLogin("benjamin")
        cy.selectLanguage("ENG")
        cy.projectByName("Alatskivi")
    })

    it('Does show the test meeting', function () {
        // Arrange - setup initial app state
        // -- visit a web page with a meeting
        cy.visit("#/projects/17546/meetings")
        // -- query for an element - meeting with testable topic
        let sel = 'a:contains("Test meeting #3 #1")'
        cy.get(sel).click()
        // Act - take an action
        // -- interact with that element (click to open)
        cy.get('h3:contains("Test topic")').click()
        // Assert - make assertion
        // -- make an assertion about meeting content - it should be shown

    })
})