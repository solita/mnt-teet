describe('Meeting Links', function () {
    beforeEach(() => {
        cy.dummyLogin("Danny")
        cy.selectLanguage("ENG")
        cy.randomName("testmeeting", "testmeeting")
        cy.randomName("testtopic", "testtopic")
        cy.randomName("testfileB", "testfileB")
        cy.randomName("testfileA", "testfileA")
        cy.randomName("testfileC", "testfileC")
        cy.backendCommand(":thk.project/update-cadastral-units", "{:project-id \"18463\" :cadastral-units " +
            "#{\"2:63801:001:0241\"" +
             "\"2:92901:001:0138\"" +
            " \"2:93001:001:0019\"" +
            " \"2:63801:001:0176\"" +
            "\"2:63801:001:0172\"" +
            "\"2:93001:001:0029\"" +
            "\"2:63801:001:0239\"" +
            "\"2:63801:001:0243\"" +
            "\"2:63801:001:0238\"" +
            "\"2:92901:001:0137\"" +
            "\"2:93001:001:0071\"" +
            "\"2:93001:001:0009\"" +
            "\"2:63801:001:0237\"}}")
        cy.projectByName("integration test project")
    })

    function checkCadastralUnitsSorted() {
        const search = `input[placeholder='Link...']`
        cy.get(search).type("Tee")
        cy.get("div.select-user-list div.select-user-entry:nth-child(1)").contains("58 Aluste-Kergu tee")
        cy.get("div.select-user-list div.select-user-entry:nth-child(2)").contains("58 Aluste-Kergu tee")
        cy.get("div.select-user-list div.select-user-entry:nth-child(3)").contains("58 Aluste-Kergu tee")
        cy.get("div.select-user-list div.select-user-entry:nth-child(4)").contains("Juurdepääsutee lõik 471")
        cy.get("div.select-user-list div.select-user-entry:nth-child(5)").contains("Rapla-Pärnu raudtee 471")
    }

    function checkEstatesSorted() {
        const search = `input[placeholder='Link...']`
        cy.get(search).type("36")
        cy.get("div.select-user-list div.select-user-entry:nth-child(1)").contains("4343606")
        cy.get("div.select-user-list div.select-user-entry:nth-child(2)").contains("8293650")
        cy.get("div.select-user-list div.select-user-entry:nth-child(3)").contains("11263650")
        cy.get("div.select-user-list div.select-user-entry:nth-child(4)").contains("16036950")
    }

    function uploadFile(name, fixturePath) {
        // Upload file
        let fileNameWithSuffix = name + ".jpg"
        cy.get("[data-cy=task-file-upload]").click()
        cy.uploadFile({inputSelector: "input[id=files-field]",
            fixturePath: fixturePath,
            mimeType: "image/jpeg",
            fileName: fileNameWithSuffix})
        cy.get(`input[value=${name}]`)
        cy.get("button[type=submit]").click()
    }

    function deleteFile(name) {
        // File added to file list, open file view
        cy.get("[data-cy=task-file-list] a").contains(name).click()
        cy.get("[data-cy=edit-file-button]").click()
        cy.get("#delete-button").click()

        // We get warning about all versions of file being deleted
        cy.get("p.MuiDialogContentText-root").contains("all the versions of this file")

        // Delete file
        cy.get("#confirm-delete").click()
    }

    function checkTasksSorted() {
        // TODO:implement
    }

    function checkFilesSorted() {
        const search = `input[placeholder='Link...']`
        cy.get(search).type("test")
        cy.get("div.select-user-list div.select-user-entry:nth-child(1)").contains(this.testfileA)
        cy.get("div.select-user-list div.select-user-entry:nth-child(2)").contains(this.testfileB)
        cy.get("div.select-user-list div.select-user-entry:nth-child(3)").contains(this.testfileC)
    }

    function createMeeting() {
        cy.get("button.project-menu").click()
        cy.get("li.project-menu-item-project-meetings").click()
        cy.wait(1000)

        cy.contains('Preliminary design').click()
        cy.wait(1000)

        cy.contains('Create meeting').click()
        cy.get(`input[class*=':date-input']`).type(new Date().toLocaleDateString("et-EE"))
        cy.get("[class*=start-time]").type("10:00")
        cy.get("[class*=end-time]").type("11:00")
        cy.formInput(
            ":meeting/title", this.testmeeting,
            ":meeting/location", "Test environment RAM")
        cy.formSubmit()
    }

    function createAgenda() {
        let sel = "a:contains(" + this.testmeeting + ")"
        cy.get(sel).click()

        cy.get("#add-agenda").click()
        cy.formInput(":meeting.agenda/topic", this.testtopic)
        cy.get("button[type=submit]").click({"multiple": true, "force": true})
    }

    function openTestTopic() {
        cy.get(".agenda-heading h3").contains(this.testtopic)
        cy.get(".agenda-heading h3").click()
    }

    function selectSearchItems(itemType) {
        const select = "#links-type-select"
        cy.get(itemType).then(($opt) => {
            cy.get(select).select($opt.attr("value"))
        })
    }

    it('sort searched cadastral units', function () {
        cy.get("h1").contains("integration test project")
        createMeeting.call(this)
        createAgenda.call(this)
        openTestTopic.call(this)
        selectSearchItems.call(this, `[data-item*=':cadastral-unit']`);
        cy.wait(1000)
        checkCadastralUnitsSorted()
        cy.wait(3000)
    })

    it('sort searched real estates', function () {
        cy.get("h1").contains("integration test project")
        createMeeting.call(this)
        createAgenda.call(this)
        openTestTopic.call(this)
        selectSearchItems.call(this, `[data-item*=':estate']`);
        cy.wait(1000)
        checkEstatesSorted()
        cy.wait(3000)
    })


    it('sort searched files', function () {
        cy.get("li a").contains("Detailed design").click()
        cy.wait(1000)
        cy.get("li a").contains("Design requirements").click()
        cy.wait(1000)

        uploadFile(this.testfileA, "text_file.jpg")
        cy.wait(3000)
        uploadFile(this.testfileB, "text_file2.jpg")
        cy.wait(3000)
        uploadFile(this.testfileC, "text_file3.jpg")
        cy.wait(3000)

        cy.projectByName("integration test project")
        cy.get("h1").contains("integration test project")
        createMeeting.call(this)
        createAgenda.call(this)

        openTestTopic.call(this)
        cy.wait(1000)

        selectSearchItems.call(this, `[data-item*=':file']`)
        cy.wait(3000)

        checkFilesSorted.call(this)
        cy.wait(3000)

        // Cleanup, should be removed as we get isolated test environment
        cy.projectByName("integration test project")
        cy.get("li a").contains("Detailed design").click()
        cy.get("li a").contains("Design requirements").click()
        deleteFile(this.testfileA)
        cy.wait(1000)
        deleteFile(this.testfileB)
        cy.wait(1000)
        deleteFile(this.testfileC)
        cy.wait(1000)
    })
})
