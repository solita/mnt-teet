describe("Task view", function() {
  before(() => {
    cy.randomName("filename", "myfile")
    cy.dummyLogin("benjamin")
    cy.selectLanguage("ENG")
    cy.projectByName("cooperation test")

    // Navigate to Detailed design -> Design requirements
    cy.get("li a").contains("Detailed design").click()
    cy.get("li a").contains("Design requirements").click()
  })

  context("File upload", function() {

    it("new file can be added and deleted", function() {
      // Upload file
      let fileNameWithSuffix = this.filename + ".jpg"
      cy.get("[data-cy=task-file-upload]").click()
      cy.uploadFile({inputSelector: "input[type=file]",
                     fixturePath: "text_file.jpg",
                     mimeType: "image/jpeg",
                     fileName: fileNameWithSuffix})
      cy.get(`input[value=${this.filename}]`)
      cy.get("button[type=submit]").click({force: true})

      // File added to file list, open file view
      cy.get("[data-cy=task-file-list] a").contains(this.filename).click()
      cy.get("[data-cy=edit-file-button]").click()
      cy.get("#delete-button").click()

      // We get warning about all versions of file being deleted
      cy.get("p.MuiDialogContentText-root").contains("all the versions of this file")

      // Delete file
      cy.get("#confirm-delete").click()
    })
  })
})
